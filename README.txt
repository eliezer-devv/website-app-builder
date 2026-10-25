GENERIC WEBSITE APP BUILDER
===========================

A reusable Android WebView wrapper and GitHub Pages builder. It is designed to turn a configured HTTPS website into an Android APK.

WHAT THE BUILDER DOES
---------------------
The docs/index.html page asks for:
- Approved HTTPS website URL
- App display name
- Android package name (suggested from the website domain)
- Optional launcher icon upload
- GitHub repository owner, repository name, and a fine-grained access token

It commits app-config.properties and, when supplied, webapp-icon.png to the repository, starts the Build APK workflow, waits for completion, and offers the artifact for download. The token is held in page memory only and sent directly to api.github.com; it is not stored in local storage or sent to a separate server.

ONE-TIME SETUP
--------------
1. Upload this project's contents to a GitHub repository. The default branch should be named main.
2. In Settings > Pages, choose Deploy from a branch, branch main, folder /docs, then save.
3. Wait for GitHub Pages to publish. The URL usually looks like https://YOUR-USERNAME.github.io/YOUR-REPOSITORY/.
4. Create a fine-grained personal access token limited to this repository, with Contents: Read and write and Actions: Read and write.
5. Open the published page and enter the website, app name, package name, icon (optional), repository details, and token. Confirm the commit/build and press Build APK.

USE EACH TIME
-------------
1. Open the builder page.
2. Enter the HTTPS website and app name. The package name is suggested from the domain; review it before building.
3. Upload a square icon if desired. If you skip the icon, the generic globe icon or previously committed icon is used.
4. Enter repository owner/name and token, confirm, and click Build APK.
5. When the build succeeds, click Download APK. The artifact downloads as a ZIP; extract app-debug.apk.

PACKAGE NAME AND UPDATES
------------------------
Each distinct app should have a unique package name. Keep the package name and signing key unchanged to update an existing installation. This workflow creates a debug-signed APK; configure release signing with a persistent private key before relying on long-term public distribution.

SECURITY AND LIMITATIONS
------------------------
- GitHub Pages is static, so the builder uses a narrowly scoped GitHub token to update files and trigger builds. Never paste a token on a public/shared computer; refresh or close the page when done.
- App settings, website URL, and uploaded icon are committed to the selected repository. Use a private repository if these should remain private. GitHub Pages visibility depends on your GitHub plan and organization settings.
- The app restricts top-level navigation to the configured hostname. It is not a device-wide web filter; embedded resources from other hosts can still load.
- If a build fails, inspect the Actions tab. Ensure the workflow is enabled and the default branch is main.
