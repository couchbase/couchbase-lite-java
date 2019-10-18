pipeline {
    agent { label 'mobile-mac-mini'  }
    stages {
        stage('Prepare'){
            steps {
                sh """
                    git clone https://github.com/couchbase/couchbase-lite-java.git
                    git clone https://github.com/couchbaselabs/couchbase-lite-java-ee.git
                    git clone https://github.com/couchbase/couchbase-lite-core-EE.git
                    git clone https://github.com/couchbase/couchbase-lite-core.git
                    pushd couchbase-lite-core
                    git submodule update --init --recursive
                    popd
                    echo "0.0.0" > version.txt
                """
            }
        }
        stage('Build'){
            steps {
                sh """
                    pushd couchbase-lite-java-ee
                    touch local.properties
                    ../couchbase-lite-java/scripts/build_litecore.sh -e EE
                    ./gradlew build
                    popd
                """
            }
        }
    }
}
