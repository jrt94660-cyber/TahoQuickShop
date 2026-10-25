# TahoQuickShop

A clean-room Paper shop plugin inspired by common configurable SMP shop features. This is an independent implementation; it does not patch or remove licensing from SMPShop.

## Current baseline
- Configurable category GUI and item menus
- Configurable materials, names, lore, prices, and stack amounts
- Vault economy support for purchases
- Optional console reward commands per configured item
- Inventory-space checks and purchase feedback
- Pagination and configurable filler/navigation items
- No commands are registered yet, per project requirements

## Build
Requires Java 21 and Maven:
`mvn -U clean package`

Output: `target/TahoQuickShop.jar`

## Opening the GUI
The initial version intentionally has no command. Other plugin code can call the public API method `TahoQuickShop#openMainShop(Player)`. A command can be added later once its name and behavior are specified.

## Configuration
Edit `plugins/TahoQuickShop/config.yml`. Vault and a compatible economy provider are needed for the built-in money flow.
