require 'fileutils'

def uploadArchives() 
  puts ".................................couchbase-lite-java-native"
  uploadArchivesSingleLibrary("libraries:couchbase-lite-java-native",  "build",    "")
  
  puts ".................................couchbase-lite-java"
  uploadArchivesSingleLibrary("libraries:couchbase-lite-java",         "build",    "buildAndroidWithArtifacts")
end

# upload the archives for a single library,
def uploadArchivesSingleLibrary(libraryName, buildCommand, systemProperty)
  cmd = "./gradlew :#{libraryName}:#{buildCommand}"
  cmd = "#{cmd} -D#{systemProperty}" if !systemProperty.empty?
  puts "-------------------------------------------------------"
  runCommandCheckError cmd
  
  cmd = "./gradlew :#{libraryName}:uploadArchivesWrapper"
  cmd = "#{cmd} -D#{systemProperty}" if !systemProperty.empty?
  puts "-------------------------------------------------------"
  runCommandCheckError cmd
  puts "-------------------------------------------------------"

end

def buildAndPackage() 
  # make sure we are in the correct place
  assertPresentInCurrentDirectory(["settings.gradle"])

  # clean
  puts "Cleaning previous build ..."
  puts "-------------------------------------------------------"
  cmd = "./gradlew clean"
  runCommandCheckError cmd

  # build the code
  puts "Building and Packaging ..."
  puts "-------------------------------------------------------"
  cmd = "./gradlew distZip"
  runCommandCheckError cmd  
end

def assertPresentInCurrentDirectory(file_list) 
  Dir.foreach('.') do |item|
    next if item == '.' or item == '..'
    if file_list.include? item 
      file_list.delete item
    end
  end

  raise "Did not find all %s in current dir" % file_list if file_list.size() != 0
end

def runCommandCheckError(cmd)
  puts cmd 
  result = %x( #{cmd} )
  puts result

  if ($?.exitstatus != 0) 
    puts "Error, aborting"
    exit($?.exitstatus)
  end
end
