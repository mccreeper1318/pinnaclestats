# PinnacleStats v1.0.10

PinnacleStats exports formatted Minecraft player statistics for the Pinnacle SMP website.

## This update

v1.0.10 fixes a startup error on newer Paper 26.2 builds where event listener registration was compiled against the wrong method signature.

## Install

1. Stop the server.
2. Delete the old PinnacleStats jar from `plugins/`.
3. Upload `dist/PinnacleStats-1.0.10.jar` to `plugins/`.
4. Keep your existing `plugins/PinnacleStats/config.yml`.
5. Start the server.
6. Run `/pstats status`.

No website or config changes are required from v1.0.9.
