#/bin/bash
gradle clean && gradle shadow
cp build/libs/glouton-all.jar releases/glouton.jar
sudo docker-compose -f docker-compose.yml build