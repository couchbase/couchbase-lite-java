#!/usr/bin/ruby

require "./build_automation.rb"

def verifyEnvVariable(envVarName) 
  raise "Neet do set #{envVarName}" if ENV[envVarName] == nil 
end

verifyEnvVariable("MAVEN_UPLOAD_VERSION")
verifyEnvVariable("MAVEN_UPLOAD_USERNAME")
verifyEnvVariable("MAVEN_UPLOAD_PASSWORD")
verifyEnvVariable("MAVEN_UPLOAD_REPO_URL")

uploadArchives()
