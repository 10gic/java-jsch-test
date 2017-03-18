# A simple test for JSch
This is a simple test for JSch, send a command to remote machine.

[JSch](http://www.jcraft.com/jsch/) allows you to connect to an sshd server and use port forwarding, X11 forwarding, file transfer, etc., and you can integrate its functionality into your own Java programs.

# Build
`mvn clean package`

# Run
`mvn exec:java -D exec.mainClass=com.mycompany.app.App`
