// --
// secondary jenkins initialization sequence script v1.0.0.0
// --
// @created_at: 2020-02-26
// @created_by: post@dunkelfrosch.com
// @updated_at: 2020-04-17
// --
// this script handles the actual update sequence of all plugins of the jenkins build system
// involved and loaded at start time in a fully automated matter.
// --
// @source https://gist.github.com/alecharp/d8329a744333530e18e5d810645c1238
//

jenkins.model.Jenkins.getInstance().getUpdateCenter().getSites().each { site ->
  site.updateDirectlyNow(hudson.model.DownloadService.signatureCheck)
}

hudson.model.DownloadService.Downloadable.all().each { downloadable ->
  downloadable.updateNow();
}

def plugins = jenkins.model.Jenkins.instance.pluginManager.activePlugins.findAll {
  it -> it.hasUpdate()
}.collect {
  it -> it.getShortName()
}

println "[init] -- plugins to upgrade: ${plugins}"
long count = 0

jenkins.model.Jenkins.instance.pluginManager.install(plugins, false).each { f ->
  f.get()
  println "${++count}/${plugins.size()}.."
}

if(plugins.size() != 0 && count == plugins.size()) {
  jenkins.model.Jenkins.instance.safeRestart()
}

println '--------------------------------------------------'
println 'jenkins update plugin script procedure finalized!'
println '--------------------------------------------------'
