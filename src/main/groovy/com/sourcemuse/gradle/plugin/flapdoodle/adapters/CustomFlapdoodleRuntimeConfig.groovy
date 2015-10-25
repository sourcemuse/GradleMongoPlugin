package com.sourcemuse.gradle.plugin.flapdoodle.adapters
import com.sourcemuse.gradle.plugin.ProxyDetails
import de.flapdoodle.embed.mongo.Command
import de.flapdoodle.embed.mongo.config.ArtifactStoreBuilder
import de.flapdoodle.embed.mongo.config.DownloadConfigBuilder
import de.flapdoodle.embed.mongo.config.RuntimeConfigBuilder
import de.flapdoodle.embed.process.distribution.Distribution
import de.flapdoodle.embed.process.distribution.IVersion
import de.flapdoodle.embed.process.runtime.ICommandLinePostProcessor

class CustomFlapdoodleRuntimeConfig extends RuntimeConfigBuilder {
    private final IVersion version
    private final String mongodVerbosity
    private final String downloadUrl
    private final ProxyDetails proxyDetails

    CustomFlapdoodleRuntimeConfig(IVersion version, String mongodVerbosity, String userSuppliedDownloadUrl, ProxyDetails proxyDetails) {
        this.version = version
        this.mongodVerbosity = mongodVerbosity
        this.downloadUrl = determineDownloadUrl(userSuppliedDownloadUrl)
        this.proxyDetails = proxyDetails
    }

    static String determineDownloadUrl(String s) {
        s ?: new DownloadConfigBuilder().build().downloadPath
    }

    @Override
    RuntimeConfigBuilder defaults(Command command) {
        super.defaults(command)
        applyProxySettings()

        commandLinePostProcessor(new ICommandLinePostProcessor() {
            @Override
            List<String> process(Distribution distribution, List<String> args) {
                if (mongodVerbosity) args.add(mongodVerbosity)
                return args
            }
        })

        def downloadConfig = new DownloadConfigBuilder()
                .defaultsForCommand(command)
                .progressListener(new CustomFlapdoodleProcessLogger(version))
                .downloadPath(downloadUrl).build()

        artifactStore().overwriteDefault(new ArtifactStoreBuilder().defaults(command).download(downloadConfig).build())

        this
    }

    private void applyProxySettings() {
        if (proxyDetails.provided()) {
            applyProxySelector()
            applyAuthenticator()
        }
    }

    private void applyProxySelector() {
        def defaultProxySelector = ProxySelector.getDefault()
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            List<Proxy> select(URI uri) {
                if (uri.getHost() == downloadUrl) {
                    return [new Proxy(Proxy.Type.HTTP, proxyDetails.socketAddress)]
                } else {
                    return defaultProxySelector.select(uri)
                }
            }

            @Override
            void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            }
        })
    }

    private void applyAuthenticator() {
        if (proxyDetails.authDetailsProvided()) {
            Authenticator.setDefault(new Authenticator() {
                @Override
                PasswordAuthentication getPasswordAuthentication() {
                    new PasswordAuthentication(proxyDetails.user, proxyDetails.password.toCharArray())
                }
            })
        }
    }
}
