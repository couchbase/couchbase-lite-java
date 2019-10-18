pipeline {
    agent { label 'mobile-mac-mini'  }
    environment {
       PRODUCT = 'couchbase-lite-java'
    }
    stages {
        stage('Checkout'){
            steps {
                sh """
                    git clone https://github.com/couchbase/couchbase-lite-java.git
                    git clone https://github.com/couchbase/couchbase-lite-core.git
                    pushd couchbase-lite-core
                    git submodule update --init --recursive
                    popd
                """
            }
        }
        stage('Build'){
            steps {
                sh """
                    echo "0.0.0" > version.txt
                    pushd couchbase-lite-java
                    touch local.properties
                    ./scripts/build_litecore.sh -e CE 
                    ./gradlew build
                    popd
                """
            }
        }
    }
}
