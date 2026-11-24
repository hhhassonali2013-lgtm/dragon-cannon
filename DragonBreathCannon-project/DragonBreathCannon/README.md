# DragonBreathCannon (Paper 1.21.11)

Orbital dragon breath cannon inspired by Wemmbu's orbital strike cannon.
Dragon breath charges fall from the sky and leave real dragon breath clouds (Harming II) where they land.

## Modes
- **normal** - concentric circles that ripple outward from the target (like the orbital strike cannon rings).
- **stab** - one tight column of shots stacked on the same spot (stab shot).

## Commands (aliases: /dbc, /dcannon)
- `/dbc normal` / `/dbc stab` - fire at the block you are looking at
- `/dbc normal <x> <y> <z>` / `/dbc stab <x> <y> <z>` - fire at coordinates
- `/dbc give <normal|stab> [player]` - gives a Dragon's Breath item, right click to fire where you look
- `/dbc reload` - reload config.yml

Permissions (default op): `dragoncannon.use`, `dragoncannon.give`, `dragoncannon.reload`, `dragoncannon.bypasscooldown`

## Build the jar with GitHub (no install needed)
1. Make a new repository on github.com (private is fine).
2. Upload this whole project, including the hidden `.github` folder, `src`, `pom.xml`.
   Easiest is git:
   ```
   git init && git add -A && git commit -m "dragon breath cannon"
   git branch -M main
   git remote add origin https://github.com/YOURNAME/YOURREPO.git
   git push -u origin main
   ```
3. Open the **Actions** tab -> **Build plugin** (it also starts by itself on every push).
4. When the run is green, open it, scroll to **Artifacts**, download **DragonBreathCannon**, unzip it.
5. Put `DragonBreathCannon.jar` in your server's `plugins/` folder and restart the server.

## Build locally instead
Install JDK 21 + Maven, then run `mvn clean package`. The jar is `target/DragonBreathCannon.jar`.
