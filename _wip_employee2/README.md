Employee-2 WIP quarantined so Sprint-1 shell can assembleDebug.

Moved out of `app/src/main` on 2026-10-27 because media3 deps were incomplete /
DetailScreen did not compile, and Sprint-1 ownership keeps Detail as a placeholder.

Please re-integrate against:
- DetailActivity Intent contract (EXTRA_MEDIA_ID / EXTRA_SEASON / EXTRA_EPISODE)
- MediaApi.detail / outsideLinks / playReport already declared
- Add media3 to libs.versions.toml + app/build.gradle.kts when ready
