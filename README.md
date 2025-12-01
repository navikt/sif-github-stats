sif-github-stats
================

Naisjob som henter statistikk fra github og pusher til prometheus.

Bruker nais/google secret  med navnet `sif-stats-github-pat` for k9saksbehandling.

Secreten har 1 års varighet og trenger permissions: 

```
repo 
  repo:statusAccess 
  repo_deployment
  public_repo
  security_events
read:org
```

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