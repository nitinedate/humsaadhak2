Test-only certificates (made with deploy/tls/make-certs.sh, valid 10 years). Never use them anywhere else.
ca.crt signs server.*; rogue-server.* is signed by a different CA, to prove untrusted certificates are refused.
