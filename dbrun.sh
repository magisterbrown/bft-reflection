#gradle installDist
#jdb -sourcepath ./src/main/java -Djava.security.properties="./config/java.security" -Dlogback.configurationFile="./config/logback.xml" -classpath "build/install/library/lib/*"  bftsmart.demo.counter.CounterServer 0
ip netns exec clust1 java -Djava.security.properties="./config/java.security" -Dlogback.configurationFile="./config/logback.xml" -classpath "build/install/library/lib/*"  bftsmart.demo.counter.CounterServer 0
