bots based on the [koma library](https://github.com/koma-im/koma-library)

In the command line, add `-h` or `--help` for help.

For security, only tokens are used. You can login to riot.im and use the developer tools of the browser to copy the parameter `access_token` in requests.
Pass the access token using a environment variable named `TOKEN`.


## Troubleshooting

### TLS certificates

If you get strange certificate issues, such as:

> javax.net.ssl.SSLException: Unexpected error: java.security.InvalidAlgorithmParameterException: the trustAnchors parameter must be non-empty

A possible cause is that the Java Keystore is not correctly set up.
Check the type of the cert file, on some Linux systems, the path is
`/etc/ssl/certs/java/cacerts`, the type should be `Java KeyStore`.
On hosts where I got the error, the type is just `data`.
The cause of this probalem may be that the script used to update the
certificates has not run correctly.
On Debian, the script is `/etc/ca-certificates/update.d/jks-keystore`,
which is provided by the `ca-certificates-java` package,
and depends on Java to run.
The `stretch`(`stable`) version of the script, 20170531,
does not support Java Runtime 11.
If you have only Java 11 installed, which is available in `stretch-backports`,
you get the problem.
The solution is to install `ca-certificates-java` from `testing` or `unstable`,
which is 20180516 as of now, and does update the keystore correctly.
