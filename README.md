# Pack Toggle

Client-side Fabric mod for Minecraft **1.21.11**: save your resource pack order once, then switch all packs off and back on with one key or button.

![Three new buttons in the resource pack menu](images/menu.png)

## Why

Sometimes you need the plain default look for a moment - to check a texture, record something, or compare. Taking every pack out is quick, but putting your PvP packs and overlays back **in the right order** is not. Pack Toggle remembers the order for you.

## How to use

### In the resource pack menu

Open *Options → Resource Packs*. There are three new buttons above *Open Pack Folder* and *Done*:

| Button | What it does |
| --- | --- |
| **Save packs** | Remembers the active resource packs and their order. |
| **Remove packs** | Moves all your packs out, so only the default look is left. |
| **Add packs** | Puts the saved packs back in, in the saved order. |

The buttons only move the packs between the two lists. As always, click **Done** to apply.

![After "Remove packs": everything is back in the Available list](images/removed.png)

### With a hotkey

Press **F8** in game to switch your packs off, and again to bring them back. No menu needed.

![Hotkey in game](images/hotkey.png)

If you never saved anything, the first press saves your current packs automatically, so nothing gets lost.

Both keys can be changed under *Options → Controls → Key Binds → Pack Toggle*. A second key, *Save current resource packs*, is unbound by default.

## Welcome tour

The first time you start a profile with this mod, a short tour shows how it works - with Back and Next buttons and a Skip button that is always there. It only appears once. If you use several mods by KCLucas, they share one tour and you can pick which ones you want to see.

![Welcome tour](images/tour.png)

## Good to know

- Packs that Minecraft forces itself (the default textures, resources of mods, a server resource pack) are never touched.
- If a saved pack was deleted from the folder, the other packs are still restored and you get a short notice.
- The saved order is stored in `config/packtoggle.json`.
- Purely client-side: works on any server.
- English and German translations.

## Requirements

- Minecraft 1.21.11
- [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.2 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)

## Building

```
./gradlew build
```

The mod jar is written to `build/libs/`. `./gradlew runClientGameTest` starts a real client, creates three sample packs and checks saving, removing and restoring through the menu buttons and the hotkey.

## License

MIT
