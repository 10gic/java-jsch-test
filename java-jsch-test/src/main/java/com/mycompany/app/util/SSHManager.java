package com.mycompany.app.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

public class SSHManager {
    private JSch jsch;
    private String userName;
    private String password;
    private String remoteHost;
    private Integer remotePort;
    private Integer connectTimeout;

    private Session session;

    private static final int BUF_LEN = 1024;

    private static final Logger logger = Logger.getLogger(SSHManager.class.getName());

    public SSHManager(String userName, String password, String remoteHost, Integer remotePort) {

        jsch = new JSch();

        this.userName = userName;
        this.password = password;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.connectTimeout = 7000;      // hard-code timeout
    }

    public void connect() throws JSchException {

        session = jsch.getSession(userName, remoteHost, remotePort);
        session.setPassword(password);

        // skip host key check
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(connectTimeout);

        logger.log(Level.INFO, "User {0} connect to host {1}", new Object[] {userName, remoteHost});
    }

    public void disconnect() {
        if (session == null) {
            return;
        } else {
            session.disconnect();

            logger.log(Level.INFO, "Disconnect session for user {0} from host {1}", new Object[] {userName, remoteHost});
        }
    }

    /**
     * Send shell command to remote host, result of execution will return
     *
     * @param command
     * @return
     * @throws JSchException
     * @throws IOException
     */
    public SSHCommandResult sendCommand(String command) throws IOException, JSchException {

        logger.log(Level.INFO, "Begin execute command {0}", command);

        StringBuilder sbStdout = new StringBuilder();
        StringBuilder sbStderr = new StringBuilder();
        Integer retCode = -1;

        Channel channel = null;

        ExecutorService execService = Executors.newFixedThreadPool(2);

        InputStream stdInputstream = null;
        InputStream errInputStream = null;
        try {
            channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);

            stdInputstream = channel.getInputStream();
            errInputStream = ((ChannelExec) channel).getErrStream();

            channel.connect();

            /* Read stdout and stderr of command, must read concurrently, otherwise it may hang */
            while (!Thread.currentThread().isInterrupted()) {
                handleInputStreams(execService, stdInputstream, sbStdout, errInputStream, sbStderr);

                if (channel.isClosed()) {

                    // After channel is closed, the rest of data also need be read.
                    handleInputStreams(execService, stdInputstream, sbStdout, errInputStream, sbStderr);

                    retCode = channel.getExitStatus();

                    logger.log(Level.INFO, "Command {0} exit, exit code is {1}", new Object[] {command, retCode});
                    break;
                }
            }

        } finally {
            if (channel != null) {
                channel.disconnect();
            }

            if (execService != null) {
                execService.shutdownNow();
                try {
                    execService.awaitTermination(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    logger.log(Level.WARNING, "Interrupted exception occurs in awaiting termination. : " + e.getMessage());
                }
            }

            if (stdInputstream != null) {
                stdInputstream.close();
            }
            if (errInputStream != null) {
                errInputStream.close();
            }

        }

        return new SSHCommandResult(command, sbStdout.toString(), sbStderr.toString(), retCode);
    }

    /**
     * Send file to remote host
     *
     * @param path
     * @param remotePath
     * @throws JSchException
     * @throws SftpException
     * @throws FileNotFoundException
     */
    public void sendFile(String path, String remotePath) throws JSchException, SftpException, FileNotFoundException {

        Channel channel;

        ChannelSftp channelSftp = null;
        FileInputStream fis = null;
        try {
            channel = session.openChannel("sftp");

            channel.connect();
            channelSftp = (ChannelSftp) channel;
            channelSftp.cd(remotePath);

            File f = new File(path);
            fis = new FileInputStream(f);
            channelSftp.put(fis, f.getName(), ChannelSftp.OVERWRITE);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (channelSftp != null) {
                channelSftp.disconnect();
            }
        }
    }

    private void handleInputStreams(ExecutorService execService, final InputStream stdInputstream,
            final StringBuilder sbStdout, final InputStream errInputStream, final StringBuilder sbStderr)
            throws IOException {

        Future<Void> stdInputFuture = execService.submit(new Callable<Void>() {
            public Void call() throws IOException {
                // read stdInputstream, save it to sbStdout, and print it to System.out
                handleInputStream(stdInputstream, sbStdout, System.out);
                return null;
            }
        });
        Future<Void> errInputFuture = execService.submit(new Callable<Void>() {
            public Void call() throws IOException {
                // read errInputstream, save it to sbStderr, and print it to System.err
                handleInputStream(errInputStream, sbStderr, System.err);
                return null;
            }
        });

        awaitToBeDone(errInputFuture);
        awaitToBeDone(stdInputFuture);
    }

    /**
     * Read data from inputstream, save it to sb, and write it to ouput.
     *
     * @param inputstream
     * @param sb
     * @param output
     * @throws IOException
     */
    private void handleInputStream(InputStream inputstream, StringBuilder sb, OutputStream output) throws IOException {
        byte[] buf = new byte[BUF_LEN];
        while (inputstream.available() > 0 && !Thread.currentThread().isInterrupted()) {
            int readLen = inputstream.read(buf, 0, buf.length);
            if (readLen < 0) {
                break;
            }

            String sBuf = new String(buf, 0, readLen, "UTF-8");
            sb.append(sBuf);

            output.write(buf, 0, readLen);
        }
    }

    private void awaitToBeDone(Future<Void> future) throws IOException {
        try {
            future.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            } else {
                throw new IllegalStateException("Unknown exception occurs.", e.getCause());
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted.", e);
        }
    }
}
