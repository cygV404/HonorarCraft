# Werkzeuge

## MakeIcons.java

Erzeugt die Desktop-Icons aus einer quadratischen PNG-Quelle — `.ico` für Windows,
`.icns` für macOS und ein `.png` für das Linux-Paket.

```bash
java tools/MakeIcons.java ~/Schreibtisch/HonorarCraft_Assets/honorarcraft_icon.png \
     composeApp/src/jvmMain/composeResources/drawable
```

Braucht nur das JDK (17 genügt), kein ImageMagick und kein `icotool` — auf dieser Maschine
war beides nicht vorhanden. Die Container werden direkt geschrieben: `.ico` als Verzeichnis
mit PNG-Einträgen, `.icns` als Folge von Typ/Länge/PNG-Blöcken.

Die Größe der Quelle begrenzt das Ergebnis: aus 512×512 entsteht kein `ic10`-Eintrag
(1024×1024) für macOS-Retina. Eine größere Quelle oder `favicon.svg` wäre dafür nötig.
