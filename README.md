# Website APK Builder

Turn a website into your own Android app — without having to build the Android app manually.

**[Open the Website APK Builder](https://eliezer-devv.github.io/website-app-builder/)**

## How it works

1. Open the builder website.
2. Enter your GitHub token.
3. Enter the website URL, app name, and a unique Android package name.
4. Upload an app icon.
5. Add any additional website hostnames needed for sign-in or other features. Enable Google sign-in if needed.
6. Click **Build APK** and wait for the build to finish.
7. Download your APK and install it on your Android device.

## Features

- Create an Android app from an HTTPS website.
- Customize the app name, icon, and package name.
- Restrict website navigation to approved HTTPS hostnames.
- Support file downloads and uploads, including selecting photos and taking pictures with the camera.
- Support website camera, microphone, and location requests, subject to Android permissions.
- Configure additional hostnames for services such as sign-in.

## Important to know

- **GitHub account:** You need a GitHub token with the required permissions to build an app.
- **Website compatibility:** This is a website wrapper, not a full web browser. Some websites may require additional configuration or features that are not currently supported.
- **App updates:** Builds are currently debug-signed. Installing updates over an existing installation is not guaranteed because the signing key may change between builds. Reliable updates require a persistent signing key and the same package name.
- **Security:** Only add additional hostnames that your website genuinely needs. If you choose to remember your GitHub token in the builder, do so only on a trusted device.

## Project

This project contains the online builder, Android app source code, and automated workflow used to build APKs.
