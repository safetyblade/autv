# Android updates that preserve accounts

Use the manual **Native app** workflow for distributed builds. It signs the release
APK with one permanent key, retains `dev.prestwich.autv`, and assigns versionCode
1000 + that workflow's run number. Re-running a job reproduces its version; start a
new workflow run for a newer version. Do not replace this workflow with another
counter without preserving the highest distributed versionCode.

## One-time setup

On a trusted machine with JDK keytool, Python 3 and GitHub CLI authenticated with
repository Actions-secret management permission:

```sh
bash scripts/setup-android-signing.sh
```

The script creates the distribution identity outside the repository, uploads three
Actions secrets through GitHub CLI, and preserves the same key on subsequent runs.
The generated password protects AUTV's signing key; it is not an account password.
Keep an encrypted private backup of the entire printed signing directory. Never
commit, share in chat, attach to a PR, or delete/regenerate that identity. Missing
signing secrets make distribution fail, rather than creating a different identity.
The connected GitHub tools cannot set these secrets; setup must run with an
appropriately authorized GitHub CLI session. No permanent signing key is created
in public CI or included in its APK artifacts.

## Installation

Download the newest Native app artifact, extract app-release.apk and open it on the
device. Android should offer **Update**, retaining favourites, app preferences and
Keystore-encrypted 9Now session tokens. No silent updater or public release is added.

Earlier PR/debug APKs used disposable CI keys. Moving from one of those builds to
the permanent distribution identity may require one last uninstall/install and
9Now activation. This cannot be repaired by exporting credentials. After that,
install newer signed APKs over the existing app; do not uninstall first.

Native provider prototype workflow artifacts remain development/test builds and
must not be used as the ongoing distribution channel. Clear storage, uninstall or
a factory reset erases credentials. Nine can revoke a session independently of
AUTV updates. Tokens remain excluded from Android backup.
