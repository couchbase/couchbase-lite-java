pipeline {
    agent { label 'mobile-mac-mini'  }
    environment {
        BRANCH = "${BRANCH_NAME}"
    }
    stages {
        stage('Prepare'){
            steps {
                sh """
                    #!/bin/bash
                    set -e
                    shopt -s extglob dotglob

                    mkdir couchbase-lite-java
                    mv !(couchbase-lite-java) couchbase-lite-java

                    git clone https://github.com/couchbaselabs/couchbase-lite-java-ee.git --branch $CHANGE_TARGET
                    git clone https://github.com/couchbase/couchbase-lite-core.git --recursive
                    git clone https://github.com/couchbase/couchbase-lite-core-EE.git
                    
                    echo "0.0.0" > version.txt
                """
            }
        }
        stage('Build'){
            steps {
                sh """
                    #!/bin/bash
                    set -e
                    
                    pushd couchbase-lite-java-ee > /dev/null
                    touch local.properties
                    ../couchbase-lite-java/scripts/build_litecore.sh -e EE
                    ./gradlew build --info
                    popd > /dev/null
                """
            }
        }
    }
}
