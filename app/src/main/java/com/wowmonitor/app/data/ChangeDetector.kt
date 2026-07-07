package com.wowmonitor.app.data

class ChangeDetector {

    data class ChangeResult(
        val versionChanges: List<VersionChange>,
        val cdnChanges: List<CdnChange>,
        val newEntries: List<VersionEntry>
    )

    suspend fun detectChanges(
        dao: VersionDao,
        freshData: List<GameData>
    ): ChangeResult {
        val versionChanges = mutableListOf<VersionChange>()
        val cdnChanges = mutableListOf<CdnChange>()
        val newEntries = mutableListOf<VersionEntry>()

        for (game in freshData) {
            for (region in game.regions) {
                val existing = dao.getLatest(game.key, region.region)

                if (existing != null) {
                    // Always set previousVersion from existing entry
                    val entry = VersionEntry(
                        gameKey = game.key,
                        gameName = game.name,
                        region = region.region,
                        versionsLine = region.versionsLine,
                        cdnsLine = region.cdnsLine,
                        buildVersion = region.buildVersion,
                        buildNumber = region.buildNumber,
                        buildConfig = region.buildConfig,
                        cdnHosts = region.cdnHosts,
                        cdnPath = region.cdnPath,
                        previousVersion = existing.buildVersion
                    )

                    // Check version change
                    if (existing.buildNumber != region.buildNumber ||
                        existing.buildConfig != region.buildConfig) {
                        versionChanges.add(VersionChange(
                            gameName = game.name,
                            region = region.region,
                            oldBuild = existing.buildVersion,
                            newBuild = region.buildVersion
                        ))
                        newEntries.add(entry)
                    }
                    // Check CDN change
                    else if (existing.cdnHosts != region.cdnHosts ||
                             existing.cdnPath != region.cdnPath) {
                        cdnChanges.add(CdnChange(
                            gameName = game.name,
                            region = region.region,
                            oldHosts = existing.cdnHosts,
                            newHosts = region.cdnHosts
                        ))
                        newEntries.add(entry)
                    }
                } else {
                    // First time - save baseline with current version as previousVersion
                    // so it shows "version → version" on first download
                    newEntries.add(VersionEntry(
                        gameKey = game.key,
                        gameName = game.name,
                        region = region.region,
                        versionsLine = region.versionsLine,
                        cdnsLine = region.cdnsLine,
                        buildVersion = region.buildVersion,
                        buildNumber = region.buildNumber,
                        buildConfig = region.buildConfig,
                        cdnHosts = region.cdnHosts,
                        cdnPath = region.cdnPath,
                        previousVersion = region.buildVersion
                    ))
                }
            }
        }

        return ChangeResult(versionChanges, cdnChanges, newEntries)
    }
}
