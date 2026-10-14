# InstantStatusButton

An Android home-screen widget that instantly shares a short status on social media.

The widget is a round, colored button with a short label (up to 10 characters). When you tap it, your preset status is
posted to the networks you chose: X, LinkedIn, Facebook and Instagram. Optionally the post includes where you are:
a short place name and a Google Maps link, read from the device at the moment of the tap.

Example: the button shows **Yeah!**. Tap it and a post appears on X and Facebook saying
*"I am feeling great now! 📍 Kraków, Poland https://www.google.com/maps/…"*

The purpose is to give quick hints of emotions connected to a place, or to update your position, without opening
anything or typing.

## Features

- Round widget with a configurable color and label (max 10 characters). You can add several widgets; each has its own settings.
- Settings screen (opens when a widget is added, and later from the app icon): status text, button label, button color,
  add location, networks, and the X / LinkedIn connect buttons.
- **X** and **LinkedIn**: true one-tap posting in the background (no UI), with a toast and a notification telling you if it worked.
- **Facebook** and **Instagram**: the text is copied to the clipboard and the app opens with a generated image card (see below).
- Native Kotlin, Jetpack Compose for settings, `RemoteViews` for the widget, WorkManager for posting, minSdk 26.

## Getting the app

Every push builds a debug APK in GitHub Actions. Open the latest run of the **Build** workflow
(Actions tab) and download the artifact **InstantStatusButton-apk**. Unzip it and install `app-debug.apk`
(you need to allow installs from unknown sources).

To build locally you need JDK 17 and the Android SDK (platform 34):

```
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

## Setup

No keys are stored in this repository. You create your own developer apps for X and LinkedIn and type the client
IDs/secrets into the app. They are stored on the device in `EncryptedSharedPreferences`.

In both cases you must register this **redirect URI** exactly (it is also shown in the app under each account):

| Network  | Redirect URI                              |
|----------|-------------------------------------------|
| X        | `instantstatusbutton://oauth/x`           |
| LinkedIn | `instantstatusbutton://oauth/linkedin`    |

### X (Twitter)

1. Sign in at <https://developer.x.com/en/portal/dashboard> and create a **Project** and an **App** inside it.
2. Open the app's **User authentication settings** and click **Set up**.
3. Set **App permissions** to **Read and write**.
4. Set **Type of App** to **Native App** (public client, PKCE) or **Web App, Automated App or Bot** (confidential client). Both work.
5. Under **App info** set **Callback URI / Redirect URL** to `instantstatusbutton://oauth/x`. Website URL can be any URL you own. Save.
6. Open **Keys and tokens** and copy the **OAuth 2.0 Client ID** (and the **Client Secret**, if your app type has one).
7. In the app: *Accounts → X*. Paste the client ID (and secret, if any) and tap **Connect X**. Approve access in the browser.

The app requests the scopes `tweet.read tweet.write users.read offline.access` and posts with `POST /2/tweets`.
Access tokens last about two hours; the app renews them automatically with the refresh token.
Note that X's API access tier and its posting limits determine how many posts you can make.

### LinkedIn

1. Go to <https://www.linkedin.com/developers/apps> and **Create app** (a LinkedIn Page is required to own the app).
2. On the **Products** tab, request **Share on LinkedIn** (gives `w_member_social`) and **Sign In with LinkedIn using OpenID Connect**
   (needed once, to find your member id).
3. On the **Auth** tab, add `instantstatusbutton://oauth/linkedin` under **Authorized redirect URLs for your app**.
4. Still on the **Auth** tab, copy the **Client ID** and **Primary Client Secret**.
5. In the app: *Accounts → LinkedIn*. Paste both and tap **Connect LinkedIn**. Approve access in the browser.

Posts are created with the LinkedIn Posts API (`POST /rest/posts`) on your own profile. LinkedIn access tokens last
60 days and standard apps get no refresh token, so when it expires the app tells you to tap **Reconnect**.

### Permissions

- **Location** (only if "Add location" is on): the app asks for the foreground location permission the first time
  you enable it. Tapping the widget opens a transparent screen for a moment, so the app counts as in the foreground and
  needs **no background location permission**. If permission is missing or no fix is available, the post goes out without a location.
- **Notifications** (Android 13+): used to report whether a post succeeded or failed.

## How Facebook and Instagram sharing works

Facebook and Instagram do not let apps post to a personal profile through their APIs, and their apps ignore text
prefilled by other apps. So for these two the widget does the next best thing, in one tap:

1. Copies the status text (plus place name and Maps link) to the clipboard.
2. Generates a square 1080×1080 image card showing the status text and the place name.
3. Opens the Facebook app (`com.facebook.katana`) or Instagram (`com.instagram.android`) directly with that image
   through a share intent.
4. You paste the text and tap Post.

If both are selected, Facebook opens first and Instagram opens after you come back from it.
If an app is not installed, the Android share sheet is opened instead.

## How it works

- Tapping the widget starts a transparent activity. It reads the location (if enabled), reverse-geocodes it,
  queues one WorkManager job per API network (X, LinkedIn), and starts the Facebook/Instagram share flow.
- Each WorkManager job needs a network connection, retries network errors and rate limits with backoff, refreshes expired
  OAuth tokens, and reports the outcome with a toast and a notification.
- Per-widget settings (status, label, color, location, networks) are kept in a normal preferences file per widget id;
  secrets (client IDs/secrets, tokens) are kept in `EncryptedSharedPreferences`.
