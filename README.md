**PinnacleStats 1.0.9**  
PinnacleStats exports Minecraft player statistics into clean static JSON files for the Pinnacle SMP website.  
**Important 1.0.9 fix**  
Version 1.0.9 adds retry handling for temporary GitHub failures, including HTTP 503 Unicorn responses from GitHub.   
**Install**  
Upload dist/PinnacleStats-1.0.9.jar to your server's plugins/ folder and restart the server.  
**Manual workflow**  
/pstats refresh  
 /pstats publish  
   
/pstats export only writes local files. /pstats publish writes local files and updates GitHub.  
