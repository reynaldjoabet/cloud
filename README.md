# Cloud

- `ssh` is the client
- `sshd` is the server( Open SSH Daemon)
-The server must have `sshd` installed and running or you will not be able to cnnect using SSH


## SSO
ssologin/login
ssologin/logout

`SessionManager`, `SessionStore`
`NamespaceManager`, `NamespaceStore`
`TokenAuthenticator`
`TokenGenerator`
`GetUserAccessTokenAsync();`
`RevokeUserRefreshTokenAsync();`

## What is identity and access management (IAM)?

Identity and access management ensures that the right people, machines, and software components get access to the right resources at the right time. First, the person, machine, or software component proves they're who or what they claim to be. Then, the person, machine, or software component is allowed or denied access to or use of certain resources.

## Identity

A digital identity is a collection of unique identifiers or attributes that represent a human, software component, machine, asset, or resource in a computer system. An identifier can be:

- An email address
- Sign-in credentials (username/password)
- Bank account number
- Government issued ID
- MAC address or IP address

`Identities are used to authenticate and authorize access to resources,` communicate with other humans, conduct transactions, and other purposes.

At a high level, there are three types of identities:

- `Human identities` represent people such as employees (internal workers and frontline workers) and external users (customers, consultants, vendors, and partners).
- `Workload identities` represent software workloads such as an application, service, script, or container.
- `Device identities` represent devices such as desktop computers, mobile phones, IoT sensors, and IoT managed devices. Device identities are distinct from human identities.


## Authentication

Authentication is the process of challenging a person, software component, or hardware device for credentials in order to verify their identity, or prove they're who or what they claim to be. Authentication typically requires the use of credentials (like username and password, fingerprints, certificates, or one-time passcodes). Authentication is sometimes shortened to AuthN.

Multifactor authentication (MFA) is a security measure that requires users to provide more than one piece of evidence to verify their identities, such as:

- Something they know, for example a password.
- Something they have, like a badge or security token.
- Something they are, like a biometric (fingerprint or face).

Single sign-on (SSO) allows users to authenticate their identity once and then later silently authenticate when accessing various resources that rely on the same identity. Once authenticated, the IAM system acts as the source of identity truth for the other resources available to the user. It removes the need for signing on to multiple, separate target systems.

## Authorization

Authorization validates that the user, machine, or software component has been granted access to certain resources. Authorization is sometimes shortened to AuthZ.