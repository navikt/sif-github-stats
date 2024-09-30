sif-github-stats
================

Naisjob som henter statistikk fra github og pusher til prometheus.

# Komme i gang

Lagre PAT for github i [google secrets manager](https://cloud.google.com/secret-manager) med navnet `sif-stats-github-pat`

---

# Henvendelser

Enten:
Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #sif_saksbehandling_tech
  

# Lokal testing av Main.kt
Sett env variabler:
sif-stats-github-pat=din_PAT
GITHUB_API_URL=https://api.github.com/
PUSH_GATEWAY_ADDRESS=dummy