package com.mycompany.app;

import java.io.IOException;

import com.jcraft.jsch.JSchException;
import com.mycompany.app.util.SSHManager;

/**
 * Basic usage of JSch (JSch allows you to connect to an sshd server)
  */
public class App
{
    public static void main( String[] args )
    {
        String user = "test";
        String passwd = "12345";
        String host = "192.168.1.107";
        int port = 22;
        String command = "ifconfig -a";

        SSHManager sshInstance = new SSHManager(user, passwd, host, port);
        try {
            sshInstance.connect();
            sshInstance.sendCommand(command);
        } catch (JSchException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            sshInstance.disconnect();
        }

    }
}
