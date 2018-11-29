common = load 'ci/common.groovy'

def compile(type = 'nightly') {
  /* Disable Gradle Daemon https://stackoverflow.com/questions/38710327/jenkins-builds-fail-using-the-gradle-daemon */
  def gradleOpt = "-PbuildUrl='${currentBuild.absoluteUrl}' -Dorg.gradle.daemon=false "
  if (type == 'release') {
    gradleOpt += "-PreleaseVersion='${common.version()}'"
  }
  dir('android') {
    sh "./gradlew assembleRelease ${gradleOpt}"
  }
  def pkg = common.pkgFilename(type, 'apk')
  sh "cp android/app/build/outputs/apk/release/app-release.apk ${pkg}"
  return pkg
}

def uploadToPlayStore(type = 'nightly') {
  withCredentials([
    string(credentialsId: "SUPPLY_JSON_KEY_DATA", variable: 'GOOGLE_PLAY_JSON_KEY'),
    string(credentialsId: "SLACK_URL", variable: 'SLACK_URL')
  ]) {
    sh "bundle exec fastlane android ${type}"
  }
}

def uploadToSauceLabs() {
  def changeId = common.getParentRunEnv('CHANGE_ID')
  if (changeId != null) {
    env.SAUCE_LABS_NAME = "${changeId}.apk"
  } else {
    env.SAUCE_LABS_NAME = "im.status.ethereum-e2e-${GIT_COMMIT.take(6)}.apk"
  }
  withCredentials([
    string(credentialsId: 'SAUCE_ACCESS_KEY', variable: 'SAUCE_ACCESS_KEY'),
    string(credentialsId: 'SAUCE_USERNAME', variable: 'SAUCE_USERNAME'),
  ]) {
    sh 'bundle exec fastlane android saucelabs'
  }
  return env.SAUCE_LABS_NAME
}

def uploadToDiawi() {
  env.SAUCE_LABS_NAME = "im.status.ethereum-e2e-${GIT_COMMIT.take(6)}.apk"
  withCredentials([
    string(credentialsId: 'diawi-token', variable: 'DIAWI_TOKEN'),
  ]) {
    sh 'bundle exec fastlane android upload_diawi'
  }
  diawiUrl = readFile "${env.WORKSPACE}/fastlane/diawi.out"
  return diawiUrl
}

return this
