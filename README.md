# SaveLogins

A client-side password manager mod for Minecraft (Fabric 1.21.1+).

## Features

- **Auto-save passwords** - Automatically captures passwords when you use `/register` or `/login`
- **Quick login** - Use `/alogin` to instantly open chat with your saved login command
- **Secure storage** - Passwords are encrypted locally on your device
- **Server-specific** - Each server has its own saved password

## Commands

| Command | Description |
|---------|-------------|
| `/alogin` or `/al` | Auto-login with saved password |
| `/aregister <password>` or `/ar <password>` | Save password for current server |
| `/aremove` | Remove saved password for current server |
| `/alist` | List all saved servers |
| `/ahelp` | Show help |

## Installation

1. Download the latest `.jar` from [Releases](https://github.com/Fafiew/SaveLogins/releases)
2. Place it in your Minecraft `mods` folder
3. Requires [Fabric Loader](https://fabricmc.net/) and [Fabric API](https://modrinth.com/mod/fabric-api)

## How It Works

1. On any server, type `/register <password> <password>` → password is automatically saved
2. Next time, just type `/alogin` → chat opens with `/login <password>` → press Enter → logged in!

Works on **any server** - no mod required on the server side!

## Requirements

- Minecraft 1.21.1+
- Fabric Loader
- Fabric API

## License

MIT

## Author

[FafaHima](https://modrinth.com/user/FafaHima)
