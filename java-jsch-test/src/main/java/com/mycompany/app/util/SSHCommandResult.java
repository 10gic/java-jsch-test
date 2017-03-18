package com.mycompany.app.util;

public class SSHCommandResult {
    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getStdout() {
        return stdout;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public void setStderr(String stderr) {
        this.stderr = stderr;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    private String command;
    private String stdout;
    private String stderr;
    private int exitCode;

    public SSHCommandResult(String command, String stdout, String stderr, int exitCode) {
        super();
        this.command = command;
        this.stdout = stdout;
        this.stderr = stderr;
        this.exitCode = exitCode;
    }

}
