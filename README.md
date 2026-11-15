# PocketMirror

**Français** · [English](README.en.md)

Un miroir de poche sur l'écran verrouillé Android : un petit bouton flottant
ouvre la caméra frontale en superposition, sans déverrouiller le téléphone.

> Co-codé avec OpenCode (Gemini + DeepSeek).

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF)
![License](https://img.shields.io/badge/License-MIT-blue)

## Télécharger l'application

Vous ne voulez pas compiler ? L'APK prêt à installer est publié dans les
**[Releases](https://github.com/TalaourarAli/PocketMirror/releases/latest)**.

1. Télécharger le fichier `.apk` de la dernière release sur le téléphone.
2. Autoriser l'installation depuis cette source si Android le demande
   (navigateur ou gestionnaire de fichiers → « Installer des applications
   inconnues »).
3. Ouvrir le fichier et installer.

L'APK est signé : les mises à jour s'installent **par-dessus** l'ancienne
version, sans perdre les réglages. Seule condition : toujours la même clé de
signature (voir [Signature](#signature-de-la-release)).

**Prérequis** : Android 8.0 (API 26) ou plus récent, avec caméra frontale.

### Après installation

1. Lancer **PocketMirror** et accorder la permission **Caméra**.
2. Appuyer sur **Service Lockscreen** et activer
   « PocketMirror (Écran verrouillé) » dans les réglages d'accessibilité.
3. Verrouiller l'écran : le bouton miroir apparaît.

L'option « Afficher aussi déverrouillé » permet de tester le bouton sans
verrouiller l'écran.

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

## Ce que le service d'accessibilité fait — et ne fait pas

Un service d'accessibilité est puissant par nature, et c'est la raison pour
laquelle Android affiche un avertissement. Voici son périmètre exact dans
PocketMirror, vérifiable dans le code et dans
`app/src/main/res/xml/accessibility_service_config.xml` :

| Ne fait pas | Preuve |
|---|---|
| Ne lit **pas** le contenu des fenêtres | `android:canRetrieveWindowContent="false"` |
| Ne lit pas le texte affiché, les champs de saisie ou les mots de passe | aucune utilisation de `event.source` ni de `getNodeInfo()` |
| Ne reçoit pas les frappes clavier | `requestFilterKeyEvents=false` |
| Ne capture pas les gestes de l'utilisateur | `captureFingerprintGestures=false`, aucun `onGesture` |
| N'active pas l'exploration tactile | `requestTouchExplorationMode=false` |
| N'a aucun accès réseau | pas de permission `INTERNET` dans le manifeste |

Seul événement demandé : `TYPE_WINDOW_STATE_CHANGED` (verrouillage /
déverrouillage). Il sert uniquement de signal de rafraîchissement — le service
appelle `KeyguardManager` pour savoir si l'écran est verrouillé, sans jamais
inspecter la fenêtre notifiée.

Autrement dit, le service ne « voit » pas l'écran : il obtient le droit
d'afficher une fenêtre au-dessus du verrouillage, et rien de plus.

### Désactiver ou désinstaller proprement

**Désactiver le service** (l'app reste installée, le bouton disparaît
immédiatement) :
Paramètres → **Accessibilité** → **PocketMirror (Écran verrouillé)** →
désactiver.

**Désinstaller** :

1. désactiver d'abord le service comme ci-dessus ;
2. puis Paramètres → **Applications** → **PocketMirror** → **Désinstaller**
   (ou appui long sur l'icône → Désinstaller).

Android retire de toute façon l'autorisation d'accessibilité à la
désinstallation ; passer par la désactivation évite simplement de laisser une
entrée orpheline dans la liste des services. Les préférences sont stockées dans
le dossier privé de l'application et disparaissent avec elle — rien n'est
conservé ailleurs.

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

## Compiler depuis les sources

Prérequis : **JDK 17** et **Android SDK 36** (Android Studio, ou uniquement la
ligne de commande).

```bash
git clone https://github.com/TalaourarAli/PocketMirror.git
cd PocketMirror
./gradlew :app:assembleDebug          # APK de debug, installable directement
```

Windows :

```powershell
.\gradlew.bat :app:assembleDebug
```

APK produit : `app/build/outputs/apk/debug/app-debug.apk`, à installer avec :

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> Le SDK Android doit être trouvé par Gradle : soit via la variable
> d'environnement `ANDROID_HOME`, soit via `local.properties`
> (`sdk.dir=/chemin/vers/Android/Sdk`). Ce fichier n'est pas versionné.

## Signature de la release

Cette section concerne **uniquement** la publication d'une version pour les
autres. Pour un usage personnel, l'APK de debug suffit.

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

> ⚠️ **Conservez ce fichier et ses mots de passe.** C'est la seule chose
> irremplaçable du projet : une clé perdue ou changée oblige les utilisateurs à
> désinstaller l'application (et à perdre leurs réglages) pour installer une
> mise à jour.

## Intégration continue et releases

| Workflow | Déclencheur | Ce qu'il fait |
|---|---|---|
| `.github/workflows/android.yml` | push sur `main`, pull request, manuel | `assembleDebug` + `lintDebug`, APK de debug en artefact. **Ne publie rien.** |
| `.github/workflows/release.yml` | **manuel uniquement** | Construit l'APK de release, le signe si les secrets existent, et crée une GitHub Release avec notes générées et APK attaché |

Pousser du code ne publie rien, et pousser un tag ne publie rien non plus :
c'est volontaire. Pour publier, deux chemins possibles, exclusifs.

### Option A — signature locale (par défaut)

Le keystore reste sur votre machine, il n'est jamais transmis à GitHub.

1. Mettre à jour `versionCode` et `versionName` dans `app/build.gradle.kts`.
2. Commiter, puis taguer avec le même numéro :

   ```bash
   git tag -a v1.8 -m "Version 1.8 (versionCode 8)"
   git push origin main
   git push origin v1.8
   ```

3. Construire l'APK signé en local :

   ```bash
   ./gradlew :app:assembleRelease
   ```

4. Sur GitHub : **Releases → Draft a new release**, choisir le tag `v1.8`,
   puis joindre `app/build/outputs/apk/release/app-release.apk`
   (renommé `PocketMirror-v1.8.apk` si vous voulez suivre la convention des
   releases publiées par le workflow).

### Option B — signature dans GitHub Actions (facultatif)

Le workflow `Release` peut signer et publier lui-même, lancé à la main depuis
**Actions → Release → Run workflow**. Dans ce cas, le keystore est stocké dans
les secrets du dépôt. À ajouter dans **Settings → Secrets and variables →
Actions** :

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

> 🧹 **Supprimez `keystore.b64` juste après l'avoir collé dans GitHub Secrets.**
> Ce fichier contient votre clé privée encodée, pas chiffrée : il ne doit jamais
> être commité ni laissé dans le dossier du projet. `.gitignore` ignore
> désormais `*.b64`, mais ne comptez pas uniquement sur lui.

> ⚠️ Le keystore doit être **celui utilisé pour les versions déjà installées**.
> Un APK signé avec une autre clé ne peut pas mettre à jour l'application : il
> faut désinstaller, ce qui efface les réglages.

Sans secrets configurés, l'option B produit un APK **non signé**, qu'Android
refuse d'installer par-dessus une version existante. Dans ce cas, utilisez
l'option A.

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

Aucun analytics, aucun crash reporter, aucune bibliothèque publicitaire.

## Limitations connues

- Le service d'accessibilité est indispensable : sans lui, impossible d'afficher
  une fenêtre sur l'écran de verrouillage avec les API publiques d'Android.
- En mode *Interrupteur*, le miroir peut rester ouvert indéfiniment, et l'écran
  reste allumé tant qu'il est visible.
- **Pages mémoire de 16 Ko (Android 16 et plus)** : Android peut allouer la
  mémoire par blocs de 16 Ko au lieu de 4 Ko, et une bibliothèque native
  compilée pour l'ancienne taille ne fonctionne alors plus. CameraX embarque
  deux petites bibliothèques de ce type ; elles sont **exclues** du build pour
  éviter l'avertissement au démarrage. Conséquence concrète : quelques
  optimisations d'image optionnelles sont désactivées, l'aperçu du miroir
  fonctionne normalement.

## Licence

Distribué sous licence MIT. Voir [LICENSE](LICENSE).
