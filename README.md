# Shortcut Menu

Shortcut Menu lets the Android accessibility shortcut, such as holding both volume keys, turn your choice of screen reader or other accessibility service on or off. It works like the accessibility shortcut menu on iPhone:

*   **With no screen reader on,** the menu speaks for itself. Drag your finger over the screen to hear each choice, such as "Backtalk, off", and lift your finger to choose it. The choices fill the screen, with **Close** at the bottom, so there is always one under your finger.
*   **With a screen reader on,** the menu is a list of buttons that your screen reader reads.

Turning a screen reader on turns any other screen reader off, so two never talk at once. Services with the same name, such as Google's and Samsung's TalkBack, are told apart by their maker, such as "TalkBack (Samsung)". Pressing the shortcut again while the menu is open closes it.

Without Shortcut Menu, a shortcut with more than one feature on it asks which one to use, and that question is not spoken when no screen reader is on.

## Set up

1.  Download `shortcut-menu.apk` from the [latest release](https://github.com/aaron-gh/shortcut-menu/releases/latest) and install it. Android asks you to allow installing apps from your browser or file manager the first time.
2.  Grant it permission to turn accessibility services on and off. Android only allows this with a permission that you grant once, from a computer with adb:

    ```
    adb shell pm grant io.github.aaron_gh.shortcutmenu android.permission.WRITE_SECURE_SETTINGS
    ```

    With [Shizuku](https://shizuku.rikka.app), run the same command without `adb shell`.
3.  In **Settings**, **Accessibility**, open **Shortcut Menu** and turn on its shortcut, such as the volume keys. Make Shortcut Menu the only feature on that shortcut.
4.  Open the Shortcut Menu app and choose which services the menu offers. Until you choose, it offers your screen readers. The app also shows whether the permission is granted and what each shortcut is assigned to, and has a button to try the menu.

## How it works

The shortcut turns Shortcut Menu's accessibility service on, and the service opens the menu. When the menu closes, the service turns itself off, so the next press turns it on again. A press while the menu is open turns the service off, which closes the menu.

Choosing a service changes Android's list of enabled accessibility services directly, so Android does not ask "Allow full control?" each time. The menu speaks with the phone's text-to-speech engine, at the accessibility volume.

## Limitations

*   It needs Android 11 or later.
*   Without the permission, the menu can only say that it needs it.
*   The first time anyone uses the volume key shortcut, Android asks whether to use it. With the permission granted, Shortcut Menu marks that question as answered when you open its settings. Without it, Android speaks the question aloud.
*   A quick tap with no screen reader on chooses whatever is under the finger, like on iPhone.

## Build

```
./gradlew assembleDebug
```

Each tag such as `v0.1` builds a release APK on GitHub, signed with the release key, and publishes it as a release. Make it an annotated tag, since its message becomes the release notes: `git tag -a v0.3 -m "What changed"`.

## License

Shortcut Menu is licensed under the [Apache License 2.0](LICENSE).
