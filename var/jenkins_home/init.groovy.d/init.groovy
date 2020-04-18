#!groovy

// --
// primary jenkins initialization sequence script v1.0.0
// --
// @created_at: 2020-02-26
// @created_by: post@dunkelfrosch.com
// @updated_at: 2020-04-17
// --
// this script handles the actual initialization sequence of our containerized jenkins and additionally activates
// the authentication method and the number of job executors depending on appropriate environment variables
// --

import hudson.security.*
import hudson.security.csrf.*
import jenkins.model.*
import jenkins.security.s2m.AdminWhitelistRule
import org.jenkinsci.plugins.*
import org.jenkinsci.plugins.saml.*
import hudson.markup.RawHtmlMarkupFormatter

def isValidString = { value ->
    if (value != null && value instanceof String && value.trim() != "") {
        return true
    }

    return false
}

def env = System.getenv()
def jenkins = Jenkins.getInstance()

jenkins.getInjector().getInstance(AdminWhitelistRule.class).setMasterKillSwitch(false)

println '[init] -- start init groovy scripting sequence'

//
// configure Matrix-based Security
// --
def configureMatrixAuthorizationStrategy = { jenkinsUser, jenkinsPassword ->

    if (!isValidString(jenkinsUser)) {
        throw new Throwable("'JENKINS_USER' is required to create the initial admin user")
    }

    if (!isValidString(jenkinsPassword)) {
        throw new Throwable("'JENKINS_PASS' is required to create the initial admin password")
    }

    jenkins.setSecurityRealm(new HudsonPrivateSecurityRealm(false))
    jenkins.setAuthorizationStrategy(new GlobalMatrixAuthorizationStrategy())
    def user = jenkins.getSecurityRealm().createAccount(jenkinsUser, jenkinsPassword) ; user.save()

    jenkins.getAuthorizationStrategy().add(Jenkins.ADMINISTER, jenkinsUser)
    jenkins.save()
}

//
// configure SAML Security
// --
def configureSAMLAuthorizationStrategy = { idpMetaDataFile,
                                           displayNameAttributeName,
                                           groupsAttributeName,
                                           maximumAuthenticationLifetime,
                                           usernameAttributeName,
                                           emailAttributeName,
                                           logoutUrl,
                                           usernameCaseConversion,
                                           binding ->

    if (!isValidString(displayNameAttributeName)) {
        throw new Throwable("'JENKINS_SAML_DISPLAY_NAME_ATTRIBUTE_NAME' is required")
    }

    if (!isValidString(groupsAttributeName)) {
        throw new Throwable("'JENKINS_SAML_GROUPS_ATTRIBUTE_NAME' is required")
    }

    if (!isValidString(maximumAuthenticationLifetime)) {
        throw new Throwable("'JENKINS_SAML_MAXIMUM_AUTHENTICATION_LIFETIME' is required")
    }

    if (!isValidString(binding)) {
        throw new Throwable("'JENKINS_SAML_BINDING' is required")
    }

    if (!isValidString(idpMetaDataFile)) {
        throw new Throwable("'JENKINS_SAML_IDP_META_DATA_FILE' is required")
    }

    // load complex xml configuration for one-login-sso infrastructure jenkins from dedicated file
    def idpMetaDataConfiguration = new IdpMetadataConfiguration(new File(idpMetaDataFile).text,null,null)

    /**
     * --
     * @link: https://github.com/jenkinsci/saml-plugin/blob/master/src/main/java/org/jenkinsci/plugins/saml/SamlSecurityRealm.java
     * @link: https://www.samltool.com/generic_sso_res.php
     * --
     * @param idpMetaDataConfiguration Identity provider Metadata.
     * @param displayNameAttributeName attribute that has the displayName.
     * @param groupsAttributeName attribute that has the groups.
     * @param maximumAuthenticationLifetime maximum time that an identification it is valid.
     * @param usernameAttributeName attribute that has the username.
     * @param emailAttributeName attribute that has the email.
     * @param logoutUrl optional URL to redirect on logout.
     * @param advancedConfiguration advanced configuration settings.
     * @param encryptionData encryption configuration settings.
     * @param usernameCaseConversion username case sensitive settings.
     * @param binding SAML binding method.
     */
    def securityRealm = new SamlSecurityRealm(
        idpMetaDataConfiguration,
        displayNameAttributeName,
        groupsAttributeName,
        maximumAuthenticationLifetime.toInteger(),
        usernameAttributeName ?: "name",
        emailAttributeName ?: "email",
        logoutUrl ?: "",
        null,
        null,
        usernameCaseConversion ?: "none",
        binding ?: "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect",
        null
    );  jenkins.setSecurityRealm(securityRealm)

    def strategy = new FullControlOnceLoggedInAuthorizationStrategy()
        strategy.setAllowAnonymousRead(false)
        jenkins.setAuthorizationStrategy(strategy)

    jenkins.save()
}

// configure Authorization Strategy ('SAML', 'GitHub' or 'Matrix')
def jenkinsAuthorizationStrategy = env.JENKINS_AUTHORIZATION_STRATEGY ?: 'Matrix'
switch (jenkinsAuthorizationStrategy) {
    case "None":
        // Do nothing. We just don't want to override the security settings in Jenkins that were set up manually
        break
    case "GitHub":
        configureGitHubAuthorizationStrategy(
                env.JENKINS_GITHUB_CLIENT_ID,
                env.JENKINS_GITHUB_CLIENT_SECRET,
                env.JENKINS_GITHUB_ADMINS,
                env.JENKINS_GITHUB_ORG_NAMES,
                env.JENKINS_GITHUB_OAUTH_SCOPES
        )
        break
    case "Matrix":
        configureMatrixAuthorizationStrategy(
                env.JENKINS_USER,
                env.JENKINS_PASS
        )
        break
    case "SAML":
        configureSAMLAuthorizationStrategy(
                env.JENKINS_SAML_IDP_META_DATA_FILE,
                env.JENKINS_SAML_DISPLAY_NAME_ATTRIBUTE_NAME,
                env.JENKINS_SAML_GROUPS_ATTRIBUTE_NAME,
                env.JENKINS_SAML_MAXIMUM_AUTHENTICATION_LIFETIME,
                env.JENKINS_SAML_USERNAME_ATTRIBUTE_NAME,
                env.JENKINS_SAML_EMAIL_ATTRIBUTE_NAME,
                env.JENKINS_SAML_LOGOUT_URL,
                env.JENKINS_SAML_USERNAME_CASE_CONVERSION,
                env.JENKINS_SAML_BINDING
        )
        break
    default:
        throw new Throwable("Invalid 'JENKINS_AUTHORIZATION_STRATEGY'")
}

// set number of job executors
println '[init] -- update number of job executors'
int num_executors = 4
if (isValidString(env.JENKINS_NUM_EXECUTORS)) {
    num_executors = env.JENKINS_NUM_EXECUTORS.toInteger()
};  jenkins.setNumExecutors(num_executors)

// Enable CSRF protection
println '[init] -- activate CSRF protection'
jenkins.setCrumbIssuer(new DefaultCrumbIssuer(true))

// Disable old/unsafe agent protocols for security
println '[init] -- disable old/unsafe agent protocols for security'
jenkins.agentProtocols = ["JNLP4-connect", "Ping"] as Set

// Setup HTML formatter (Allow Markup-Formatter/SafeHTML)
println '[init] -- activate HTML formatter (Allow Markup-Formatter/SafeHTML)'
jenkins.setMarkupFormatter(new RawHtmlMarkupFormatter(false))

// disabled CLI access over TCP listener (separate port)
println '[init] -- disabled CLI access over TCP listener (separate port)'
def p = jenkins.AgentProtocol.all()
p.each { x ->
    if (x.name?.contains("CLI")) {
        println "Removing protocol ${x.name}"
        p.remove(x)
    }
}

// disable CLI access over /cli URL
println '[init] -- disable CLI access via [/cli] URL'
def removal = { lst ->
    lst.each { x ->
        if (x.getClass().name.contains("CLIAction")) {
            println "Removing extension ${x.getClass().name}"
            lst.remove(x)
        }
    }
}
removal(jenkins.getExtensionList(hudson.model.RootAction.class))
removal(jenkins.actions)

// create dedicated sub-folder(s) in /var/jenkins_home
println '[init] -- create usable docker config directory in [/var/jenkins_home/.docker]'
def proc = ['/bin/mkdir', '-p', '/var/jenkins_home/.docker'].execute(); proc.waitFor()
if (proc.exitValue() != 0)
    throw new Exception('--- [INIT_CRASH] --- Could not create a dedicated (dot)docker directory in /var/jenkins_home')

// create temp directory java reset
println '[init] -- reset java temp directory to [/var/jenkins_home]'
System.setProperty('java.io.tmpdir','/var/jenkins_home')

jenkins.save()

println '--------------------------------------------------'
println 'jenkins initialization script procedure finalized!'
println '--------------------------------------------------'
