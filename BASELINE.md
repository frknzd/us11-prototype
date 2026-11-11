# Baseline and isolation

Independent local prototype of the US11 REST verification design.

- Source: tracked tree of the team's zap-extensions US10 commit `b0b415d49aaa71247f1ba3806c2998fccddf7024`.
- Upstream licenses and copyright notices are retained. No Git history, local caches, credentials or profiles were copied.
- The repository has no remote. Do not create a remote or upload it without the user's separate request; future hosting must be private.
- `gradlew-original` is the upstream wrapper; `gradlew` scopes Java 17 to the build. Set `JAVA17_HOME` if necessary.
- Existing sibling `zap-extensions`, `zaproxy` sources and ZAP_D profile must not be modified. Core Gradle builds may update ignored outputs.
- Runtime install/profile/logs are ignored under `.local/`. Proxy: `127.0.0.1:8091`; fixture: `127.0.0.1:8092`.
- Build archives, never use `copyZapAddOn` or `copyMandatoryAddOns` (those can change the shared installation).
