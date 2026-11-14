# PocketMirror

Un miroir de poche sur l'écran verrouillé Android : un petit bouton flottant
ouvre la caméra frontale en superposition, sans déverrouiller le téléphone.

> Co-codé avec OpenCode (Gemini + DeepSeek).

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF)
![License](https://img.shields.io/badge/License-MIT-blue)

## Fonctionnalités

- **Miroir instantané** : la caméra frontale s'ouvre par-dessus l'écran de
  verrouillage, plein cadre, sans quitter la session verrouillée.
- **Bouton discret et personnalisable** : position libre sur l'écran
  (glisser-déposer), taille de 36 à 84 dp, opacité de 20 à 100 %.
- **Deux modes d'ouverture** :
  - *Maintenir appuyé* : le miroir reste ouvert tant que le doigt est posé ;
  - *Interrupteur* : un appui ouvre, l'appui suivant ferme.
- **Trois formes de fenêtre** : cercle collé au poinçon caméra, carré aux coins
  arrondis, ovale vertical.
- **Quatre cadres** : bleu festonné (miroir de poche), bleu lisse, doré, ou
  aucun cadre. Les festons ne s'appliquent qu'à la forme cercle.
- **Ajustements fins** : décalage horizontal et vertical de la popup par
  rapport au poinçon caméra, taille de la popup de 180 à 380 dp.
- **Écran maintenu allumé** tant que le miroir est ouvert
  (`FLAG_KEEP_SCREEN_ON`, sans wakelock ni permission supplémentaire).
- **Aucune connexion réseau** : l'application ne déclare même pas la permission
  `INTERNET`. Les images de la caméra ne sortent jamais de l'appareil.

## Comment ça marche

L'application repose sur un **service d'accessibilité** (`AccessibilityService`),
seul moyen pour une application Android d'afficher une fenêtre au-dessus de
l'écran de verrouillage. Ce service :

1. affiche le bouton flottant (`TYPE_ACCESSIBILITY_OVERLAY`) ;
2. crée, à l'ouverture, une seconde fenêtre d'overlay contenant un `PreviewView`
   CameraX ;
3. découpe la preview selon la forme choisie et dessine le cadre autour
   (`MaskedMirrorContainer`), avec une animation « sortie du poinçon caméra » ;
4. relâche la caméra et retire la fenêtre à la fermeture.

Le service se pré-chauffe (`ProcessCameraProvider`) pour que l'ouverture soit
immédiate. La configuration circule via `SharedPreferences` et un broadcast
interne (`ACTION_PREFS_CHANGED`), ce qui applique les réglages au miroir déjà
ouvert.

## Prérequis

- Android 8.0 (API 26) ou plus récent, avec caméra frontale ;
- JDK 17 ;
- Android SDK 36 (compileSdk / targetSdk) ;
- Android Studio (recommandé) ou uniquement la ligne de commande.

## Installation

### Depuis les sources

```bash
git clone https://github.com/<votre-compte>/PocketMirror.git
cd PocketMirror
./gradlew :app:assembleDebug          # APK de debug, installable directement
./gradlew :app:assembleRelease        # APK de release (voir signature ci-dessous)
```

Windows :

```powershell
.\gradlew.bat :app:assembleDebug
```

APK produit : `app/build/outputs/apk/debug/app-debug.apk`.

Installation par adb :

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> Le SDK Android doit être trouvé par Gradle : soit via la variable
> d'environnement `ANDROID_HOME`, soit via `local.properties`
> (`sdk.dir=/chemin/vers/Android/Sdk`). Ce fichier n'est pas versionné.

### Signature de la release

Le fichier `keystore.properties` n'est **pas** versionné (il contient les mots
de passe). Sans lui, `assembleRelease` produit un APK **non signé**, et Gradle
affiche un avertissement. Pour signer localement, créez à la racine :

```properties
storeFile=chemin/vers/pocketmirror.jks
storePassword=...
keyAlias=pocketmirror
keyPassword=...
```

Puis générez un keystore si nécessaire :

```bash
keytool -genkeypair -v -keystore pocketmirror.jks -alias pocketmirror \
        -keyalg RSA -keysize 2048 -validity 10000
```

### Après installation

1. Lancer **PocketMirror** et accorder la permission **Caméra**.
2. Appuyer sur **Service Lockscreen** et activer
   « PocketMirror (Écran verrouillé) » dans les réglages d'accessibilité.
3. Verrouiller l'écran : le bouton miroir apparaît.

L'option « Afficher aussi déverrouillé » permet de tester le bouton sans
verrouiller l'écran.

## Intégration continue et releases

Deux workflows GitHub Actions sont fournis :

| Workflow | Déclencheur | Ce qu'il fait |
|---|---|---|
| `.github/workflows/android.yml` | push sur `master`/`main`, pull request, manuel | `assembleDebug` + `lintDebug`, APK de debug en artefact. **Ne publie rien.** |
| `.github/workflows/release.yml` | push d'un tag `v*`, manuel | Vérifie que le tag correspond à `versionName`, construit l'APK de **release**, le signe si les secrets sont présents, puis crée une **GitHub Release** avec notes générées et APK attaché |

Autrement dit : **pousser du code ne publie pas de release**, seul le push d'un
tag le fait.

### Publier une version

1. Mettre à jour `versionCode` et `versionName` dans `app/build.gradle.kts`.
2. Commiter, puis taguer avec le même numéro :

   ```bash
   git tag -a v1.8 -m "Version 1.8 (versionCode 8)"
   git push origin master        # ou main
   git push origin v1.8          # ou: git push --tags
   ```

3. Le workflow `Release` vérifie la cohérence tag / `versionName`, construit
   l'APK et le publie sur la page Releases du dépôt.

### Secrets de signature (fortement recommandés)

Sans eux, la release est publiée avec un APK **non signé** ; Android refuse
d'ailleurs d'installer un APK non signé par-dessus une version déjà installée.
À ajouter dans **Settings → Secrets and variables → Actions** :

| Secret | Contenu |
|---|---|
| `KEYSTORE_BASE64` | le keystore encodé en base64 (voir ci-dessous) |
| `KEYSTORE_PASSWORD` | mot de passe du keystore (`storePassword`) |
| `KEY_ALIAS` | alias de la clé (`keyAlias`) |
| `KEY_PASSWORD` | mot de passe de la clé (`keyPassword`) |

Encodage du keystore :

```bash
base64 -w0 pocketmirror.jks > keystore.b64   # Linux
base64 -i pocketmirror.jks -o keystore.b64   # macOS
```

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("pocketmirror.jks")) | Set-Content keystore.b64
```

> Le workflow de release peut aussi être lancé à la main
> (**Actions → Release → Run workflow**) en indiquant un tag existant.

## Réglages disponibles

| Réglage | Valeurs |
|---|---|
| Mode d'ouverture | Maintenir appuyé / Interrupteur |
| Forme de la popup | Cercle / Carré arrondi / Ovale vertical |
| Cadre | Bleu festonné / Bleu lisse / Doré / Aucun |
| Hauteur de la popup | −40 à +200 dp par rapport au poinçon |
| Décalage horizontal | −120 à +120 dp |
| Taille de la popup | 180 à 380 dp |
| Taille du bouton | 36 à 84 dp |
| Transparence du bouton | 20 à 100 % |

## Structure du projet

```
app/src/main/java/com/pocketmirror/app/
├── MainActivity.kt                         # écran de réglages
├── ButtonPositionActivity.kt               # glisser-déposer du bouton
├── LockScreenMirrorAccessibilityService.kt # overlays, caméra, animations
├── MaskedMirrorContainer.kt                # découpe + cadre dessiné
├── MirrorPrefs.kt                          # SharedPreferences + broadcast
└── MirrorStyle.kt                          # InteractionMode, MirrorShape, MirrorFrame
```

## Vie privée

L'application ne dispose d'aucune permission réseau et n'envoie aucune donnée.
La caméra est utilisée uniquement pour l'aperçu local, pendant que le miroir est
ouvert. Les préférences restent dans le stockage privé de l'application.

## Limitations connues

- Le service d'accessibilité est indispensable : sans lui, impossible d'afficher
  une fenêtre sur l'écran de verrouillage avec les API publiques d'Android.
- En mode *Interrupteur*, le miroir peut rester ouvert indéfiniment, et l'écran
  reste allumé tant qu'il est visible.
- Les bibliothèques natives CameraX alignées sur 4 Ko sont exclues du packaging
  pour éviter l'avertissement 16 Ko d'Android 16/17 ; cela désactive certaines
  optimisations d'image optionnelles.

## Licence

Distribué sous licence MIT. Voir [LICENSE](LICENSE).
