# Agalar Hack — Proje Açıklaması

Bu doküman, projenin **şu anki hâlini** (branch: `claude/modules-commands-v26.2-usu5v6`)
baştan sona, dosya dosya ve özellik özellik anlatır. Amaç, kod tabanına hiç
bakmadan projenin ne yaptığını, nasıl çalıştığını ve neyin eksik/kısıtlı
olduğunu anlayabilmek.

> Eski (1.12.2 / Forge) sürüm hâlâ `og` branch'inde duruyor ve ayrı bir
> projedir; bu doküman sadece güncel Fabric/26.2 sürümünü kapsar.

---

## 1. Proje ne yapıyor?

**Agalar Hack**, Minecraft 26.2 için yazılmış, **Fabric** üzerinde çalışan,
client-side bir "utility mod" (anarşi sunucularında kullanılan, açılıp
kapatılabilen hile/yardımcı özellik koleksiyonu — "hack client"). Kullanıcı
oyun içinde sohbet kutusuna `.` ile başlayan komutlar yazarak modülleri
açıp kapatıyor, tuşlara bağlıyor ve ayarlarını değiştiriyor.

Mod tamamen **client-side**'dır: sunucuya herhangi bir şey yüklemez, sadece
kendi istemcinizde çalışır. Bu yüzden `fabric.mod.json` içinde
`"environment": "client"` olarak işaretlidir.

---

## 2. Teknoloji yığını

| Katman | Seçim |
| --- | --- |
| Oyun sürümü | Minecraft **26.2** |
| Mod loader | **Fabric** (Fabric Loader 0.19.3) |
| Build sistemi | **Fabric Loom** 1.17.19 (Gradle plugin'i) |
| Mapping | **Mojang resmi mapping'leri** (Yarn değil — bkz. §2.1) |
| API | **Fabric API** 0.157.0+26.2 |
| Dil / JDK | **Java 25** |
| Build aracı | **Gradle 9.5.1** (wrapper ile) |
| Config formatı | JSON (Gson ile, Fabric'in kendi config dizininde) |

Bu bilgiler `gradle.properties` dosyasında tutuluyor, yani sürüm
güncellemesi gerektiğinde tek dosyayı değiştirmek yeterli:

```properties
minecraft_version=26.2
loader_version=0.19.3
loom_version=1.17.19
fabric_api_version=0.157.0+26.2
mod_version=26.2
```

### 2.1. Önemli: Yarn artık kullanılmıyor

Minecraft 26.x ile birlikte Fabric, **Yarn mapping'lerini bıraktı** ve Mojang'ın
kendi resmi mapping'leri üzerine geçti. Yarn'ın en son yayınlandığı sürüm
1.21.11; 26.x için hiç Yarn build'i yok. Bunun pratik sonuçları:

- `build.gradle` içinde artık **`mappings` bağımlılığı yok**.
- Tüm Minecraft sınıf/metot isimleri değişti. Örnekler:

| Eski (Yarn) | Yeni (Mojang) |
| --- | --- |
| `MinecraftClient` | `Minecraft` |
| `ClientPlayerEntity` | `LocalPlayer` |
| `Text` | `Component` |
| `Formatting` | `ChatFormatting` |
| `TextRenderer` | `Font` |
| `Vec3d` | `Vec3` |
| `Hand` | `InteractionHand` |
| `InputUtil` | `InputConstants` |
| `StatusEffects` / `StatusEffectInstance` | `MobEffects` / `MobEffectInstance` |
| `mc.world` | `mc.level` |
| `mc.interactionManager` | `mc.gameMode` |
| `getVelocity()` / `setVelocity()` | `getDeltaMovement()` / `setDeltaMovement()` |
| `isSneaking()` | `isShiftKeyDown()` |
| `isTouchingWater()` | `isInWater()` |

`build.gradle`, bu değişkenleri okuyup Loom'a bağımlılık olarak veriyor;
ayrıca `processResources` adımı `fabric.mod.json` içindeki `${version}`
yer tutucusunu gerçek sürümle değiştiriyor (Gradle'daki `version` alanı
`mod_version`'dan geliyor).

---

## 3. Giriş noktası: `AgalarHackClient`

`src/main/java/me/mrhakan/agalarhack/AgalarHackClient.java`

Bu, modun **Fabric entrypoint**'i — `fabric.mod.json` içinde
`"client": ["me.mrhakan.agalarhack.AgalarHackClient"]` olarak tanımlı ve
`ClientModInitializer` arayüzünü uyguluyor. Oyun Fabric loader tarafından
başlatılırken `onInitializeClient()` bir kere çağrılıyor.

Bu metod başlangıçta dört şey yapıyor:

1. **`moduleManager.loadModules()`** — kayıtlı ayarları diskten okuyup
   modüllere uygular, daha önce açık bırakılmış modülleri yeniden açar.
2. **`CommandManager.init()`** — komut listesini (`help`, `modules`,
   `toggle`, `bind`, `set`) hazırlar.
3. **`ClientSendMessageEvents.ALLOW_CHAT`** olayına kayıt olur — oyuncu
   sohbete bir şey yazdığında bu callback tetiklenir; mesaj `.` ile
   başlıyorsa `CommandManager.handleChat()` onu işler ve mesajın
   **sunucuya gitmesini engeller** (`return !handled`).
4. **`ClientTickEvents.END_CLIENT_TICK`** olayına kayıt olur — her oyun
   tick'inin (saniyede 20 kez) sonunda `KeybindManager.tick()` ve
   `moduleManager.tick()` çalışır.
5. **`HudElementRegistry.addLast(...)`** ile HUD'a bir `HudElement`
   eklenir — 26.x'te `HudRenderCallback` kaldırıldı, yerine kayıtlı HUD
   elemanları geldi (bkz. §7).

Ayrıca burada modun sabitleri tanımlı:

```java
public static final String NAME = "Agalar Hack";
public static final String MOD_ID = "agalarhack";
public static final String VERSION = "26.2";
public static String prefix = ".";
```

`moduleManager` ve `SETTINGS_MANAGER` de burada `static` olarak tutuluyor,
yani projedeki her sınıf `AgalarHackClient.moduleManager` üzerinden tüm
modüllere erişebiliyor (basit bir global-state / singleton yaklaşımı).

---

## 4. Modül sistemi

### 4.1. `Module` — temel sınıf

`src/main/java/me/mrhakan/agalarhack/module/Module.java`

Her özellik (Aura, Speed, Flight...) bu sınıftan türüyor. Sağladığı şeyler:

- **Kimlik bilgisi**: `name`, `displayName` (varsayılan olarak `name`'e
  düşer), `description`, `category` (bkz. §4.2).
- **Durum**: `toggled` (açık mı kapalı mı) — `isToggled()` ile okunur.
- **Ayarlar** (`settings`, bkz. §5): her modülün kendi `Settings`
  nesnesi var; `enabled` ve `keybind` her modülde otomatik var, geri
  kalanı modül kendi `selfSettings()` metodunda ekler.
- **Yaşam döngüsü kancaları** (hepsi boş, alt sınıflar override eder):
  - `onEnable()` — modül açıldığında bir kere.
  - `onDisable()` — modül kapandığında bir kere.
  - `onUpdate()` — modül **açıkken her tick** çağrılır (asıl mantık
    genelde burada).
  - `onToggle()` — açılma/kapanma anında, enable/disable'dan önce.
  - `selfSettings()` — modüle özel varsayılan ayarları tanımlamak için.
- **`toggle()`**: durumu tersine çevirir, ilgili kancayı çağırır, yeni
  durumu `settings["enabled"]`'e yazar ve **her toggle'da ayarları diske
  kaydeder** (`AgalarHackClient.SETTINGS_MANAGER.updateSettings()`).
- **Yardımcı okuyucular**: `getNumberSetting(name, default)` ve
  `getBooleanSetting(name, default)` — ayar değerini tip güvenli şekilde
  okur, string olarak kaydedilmiş sayıları da tolere eder.
- **`getKey()`**: kayıtlı `keybind` ayarını tuş koduna çevirir; ayar
  yoksa veya bozuksa `InputConstants.UNKNOWN.getValue()` döner.

`Module` **kendi başına hiçbir olaya kayıt olmuyor** — modüllerin
tick'lenmesini `ModuleManager.tick()` yönetiyor (bkz. §4.3). Bu, eski
1.12.2 sürümündeki "her modül kendi event bus kaydını yapıyor" tasarımına
göre daha basit ve merkezi.

### 4.2. `Category` — kategori enum'u

`src/main/java/me/mrhakan/agalarhack/module/Category.java`

```java
COMBAT, EXPLOITS, MOVEMENT, RENDER, MISC, WORLD
```

Şu an sadece **COMBAT** (Aura), **MOVEMENT** (6 modül) ve **RENDER**
(Fullbright) kategorilerinde modül var; `EXPLOITS`, `MISC`, `WORLD`
enum'da tanımlı ama içi boş — ileride eklenecek modüller için hazır
altyapı.

### 4.3. `ModuleManager` — modül kayıt defteri

`src/main/java/me/mrhakan/agalarhack/managers/ModuleManager.java`

- Kurucusunda (`ModuleManager()`) **tüm modül örneklerini** oluşturup
  `modules` listesine ekler. Şu an sırayla:
  `Aura`, `Speed`, `Flight`, `Jesus`, `Sprint`, `Step`, `NoFall`,
  `Fullbright`.
- **`tick(client)`**: oyuncu ve dünya yüklüyse (`client.player != null`),
  açık olan her modülün `onUpdate()`'ini çağırır. Bu, `AgalarHackClient`
  içindeki `END_CLIENT_TICK` olayından her tick'te tetiklenir.
- **`getModule(name)`**: isimden modül bulur (büyük/küçük harf duyarsız)
  — komutlar bunu kullanır.
- **`getModulesByCategory(category)`**: `static` yardımcı, `.modules`
  komutunda kategoriye göre gruplama için kullanılır.
- **`loadModules()`**: `SettingsManager.loadSettings()`'i çağırır (diskten
  ayarları okur/uygular), ardından `enabled=true` olarak kaydedilmiş
  modülleri otomatik açar — yani **oyunu kapatıp açtığınızda, kapatmadan
  önce açık bıraktığınız modüller kendiliğinden tekrar açılır.**

### 4.4. Şu an mevcut modüller

Aşağıdaki tablo her modülün davranışını **kod seviyesinde** özetliyor.

#### Combat / Aura
`module/combat/Aura.java`

- **Ne yapar**: Belirlenen menzil içindeki en yakın canlı varlığa otomatik
  saldırır.
- **Ayarlar**: `range` (varsayılan 4.0 blok), `delay` (varsayılan 10 tick
  bekleme — saldırı sonrası cooldown), `players` (oyunculara saldırsın
  mı, varsayılan `true`), `mobs` (yaratıklara saldırsın mı, varsayılan
  `true`).
- **Mantık**: her tick'te önce cooldown sayacını azaltır; sıfırsa
  dünyadaki tüm `LivingEntity`'leri tarar, kendisi olmayan / ölü olmayan
  / spectator olmayan / `players`-`mobs` filtresine uyan varlıklar
  arasından **kare mesafeye göre en yakınını** bulur (kök alma
  maliyetinden kaçınmak için `squaredDistanceTo` kullanılıyor). Hedef
  varsa `mc.interactionManager.attackEntity(...)` ile saldırır, elini
  sallar (`swingHand`) ve cooldown'u `delay` değerine resetler.
- **Not**: Bu basit bir "kill aura" — hedef seçimi menzil/tip filtresine
  dayalı, görüş hattı (line-of-sight) veya engel kontrolü **yok**.

#### Movement / Speed
`module/movement/Speed.java`

- **Ne yapar**: Yerde yürürken hızı artırır.
- **Ayar**: `multiplier` (varsayılan 1.2).
- **Mantık**: sadece oyuncu yerdeyse ve ileri/yana hareket girdisi varsa,
  yatay hız vektörünü (`velocity.x`, `velocity.z`) her tick `multiplier`
  ile çarpar. Kod içindeki yorumda açıklandığı gibi, oyunun kendi zemin
  sürtünmesi bu çarpanı dengeliyor, yani hız sonsuza kadar büyümüyor,
  bir dengeye (equilibrium) oturuyor.

#### Movement / Flight
`module/movement/Flight.java`

- **Ne yapar**: Creative moddaki gibi uçma yeteneği verir.
- **Ayar**: `speed` (varsayılan 0.1).
- **Mantık**: her tick `abilities.mayfly` ve `abilities.flying`'i
  `true` yapar, uçuş hızını ayar değerine set eder. Kapatıldığında,
  oyuncu **gerçekten creative modda değilse** (`abilities.instabuild`)
  uçuşu kapatır ve hızı varsayılan `0.05f`'e döndürür (creative modda
  kapatırsa oyuncunun
  normal creative uçuşunu bozmamak için özel olarak korunuyor).

#### Movement / Jesus
`module/movement/Jesus.java`

- **Ne yapar**: Su veya lav üzerinde yürümeyi sağlar.
- **Ayar yok.**
- **Mantık**: oyuncu sneak (kırılıyor/çömeliyor) durumundaysa hiçbir şey
  yapmaz (bilerek dalabilsin diye). Değilse ve su/lav içindeyse dikey
  hızı `0.1`'e sabitler, böylece batmaz.

#### Movement / Sprint
`module/movement/Sprint.java`

- **Ne yapar**: İleri hareket ederken otomatik sprint (koşu) yapar.
- **Ayar yok.**
- **Mantık**: `input.hasForwardImpulse()` doğru, yatay çarpışma yok, sneak
  yapmıyor ve elinde aktif bir item kullanmıyorsa `setSprinting(true)`
  çağırır. Kapatılınca sprint'i kapatır.

#### Movement / Step
`module/movement/Step.java`

- **Ne yapar**: Zıplamadan tam blok yüksekliğinde engellerin üzerine
  çıkmayı sağlar ("step assist").
- **Ayar**: `height` (varsayılan 1.0).
- **Mantık**: `Attributes.STEP_HEIGHT` entity attribute'unu
  kullanıyor — her tick bu attribute'un base değerini ayar değerine set
  ediyor. Kapatıldığında oyunun varsayılanı olan `0.6`'ya geri
  döndürüyor.

#### Movement / NoFall
`module/movement/NoFall.java`

- **Ne yapar**: Düşme hasarını engeller.
- **Ayar yok.**
- **Mantık**: `fallDistance > 2.0f` olduğunda sunucuya
  `ServerboundMovePlayerPacket.StatusOnly(true, horizontalCollision)`
  paketi gönderir — yani
  istemci sunucuya "ben zaten yerdeyim" diyerek düşme hasarı
  hesaplamasını (sunucu tarafında) atlatmaya çalışır. Bu **sunucuya
  bağımlı bir teknik**: anti-cheat'i olan sunucularda paket incelemesiyle
  yakalanabilir.

#### Render / Fullbright
`module/render/Fullbright.java`

- **Ne yapar**: Karanlıkta görmeyi sağlar (eski "gamma hilesi" yerine).
- **Ayar yok.**
- **Mantık**: her tick oyuncuda `NIGHT_VISION` status efekti yoksa,
  süresi `INFINITE` olan, parçacıksız/ikonsuz (`showParticles=false`,
  `showIcon=false`) bir night vision efekti ekliyor. Kapatıldığında
  efekti kaldırıyor.
- **Not**: Modern Minecraft'ta gamma ayarı istemci tarafında sınırlı
  olduğu için (vanilla ayarları client-side clamp'lenmiş durumda), bu mod
  gerçek bir "gamma hack" yerine **status efekti tabanlı** bir çözüm
  kullanıyor. Bu, sunucuya efekt paketi gönderir ve bazı anti-cheat'ler
  bunu fark edebilir.

---

## 5. Ayar (Settings) sistemi

### 5.1. `Settings` — modül başına anahtar/değer deposu

`src/main/java/me/mrhakan/agalarhack/managers/Settings.java`

Basit bir `Map<String, Object>` sarmalayıcısı:

- `addSetting(name, default)` → `putIfAbsent` kullanır, yani zaten varsa
  üzerine yazmaz (kayıtlı bir değeri ezmemek için).
- `setSetting(name, value)` → doğrudan `put`.
- `getSetting(name)` → değeri döner.
- `getKeyIgnoreCase(name)` → büyük/küçük harf duyarsız anahtar arama;
  `.set` komutu kullanıcının yazdığı ayar adını gerçek anahtarla
  eşleştirmek için bunu kullanıyor.

### 5.2. `SettingsManager` — diske okuma/yazma

`src/main/java/me/mrhakan/agalarhack/managers/SettingsManager.java`

- Config dosyası: **Fabric'in resmi config dizini** üzerinden
  `FabricLoader.getInstance().getConfigDir().resolve("agalarhack.json")`
  — yani genelde `.minecraft/config/agalarhack.json`.
- **`readSettings()`**: dosya varsa Gson ile
  `Map<String, Settings>` olarak parse eder (anahtar = modül ismi);
  yoksa veya bozuksa boş map döner (hata varsa stack trace basar,
  crash etmez).
- **`writeSettings(map)`**: config klasörü yoksa oluşturur, JSON'u
  pretty-print olarak yazar.
- **`updateSettings()`**: mevcut tüm modüllerin `settings` nesnelerini
  toplayıp diske yazar. Modül `toggle()` her çağrıldığında ve `.bind` /
  `.set` komutlarında bu çağrılır — yani **her değişiklik anında
  kalıcı hale gelir**, ayrı bir "kaydet" adımı yok.
- **`loadSettings()`**: oyun açılışında çağrılır. Her modül için önce
  `registerSettings()` çağrılır (varsayılanlar kurulur), sonra diskten
  okunan kayıtlı değerler bu varsayılanların **üzerine** yazılır
  (`putAll`). Bu sıralama önemli: yeni bir sürümde modüle yeni bir ayar
  eklenirse, eski config dosyasında o ayar olmasa bile varsayılan değer
  önce kurulmuş olduğu için config **kırılmaz**, sadece yeni ayar
  varsayılanıyla eklenmiş olur.

### 5.3. Örnek config çıktısı

```json
{
  "Flight": {
    "settings": {
      "enabled": true,
      "keybind": "294",
      "speed": 0.1
    }
  },
  "Aura": {
    "settings": {
      "enabled": false,
      "keybind": "82",
      "range": 4.0,
      "delay": 10.0,
      "players": true,
      "mobs": true
    }
  }
}
```

---

## 6. Komut sistemi

### 6.1. `Command` — soyut temel sınıf

`src/main/java/me/mrhakan/agalarhack/commands/Command.java`

Her komut şu bilgilerle kurulur: `name`, `description`, `usage`,
`aliases...` (varargs). `matches(label)` metodu hem asıl ismi hem
alias'ları büyük/küçük harf duyarsız karşılaştırır. `sendUsage()`,
alt sınıfların "yanlış kullanım" durumunda çağırdığı ortak bir yardımcı.

### 6.2. `CommandManager` — sohbet mesajlarını yakalama ve yönlendirme

`src/main/java/me/mrhakan/agalarhack/managers/CommandManager.java`

- `init()`: `commands` listesine sırayla `Help`, `Modules`, `Toggle`,
  `Bind`, `Set` örneklerini ekler.
- `getCommand(label)`: yazılan ilk kelimeye (`.help`'teki `help` kısmı)
  uyan komutu bulur.
- **`handleChat(message)`**: `AgalarHackClient`'taki
  `ClientSendMessageEvents.ALLOW_CHAT` callback'inden çağrılır.
  - Mesaj `.` ile başlamıyorsa `false` döner → mesaj normal şekilde
    sunucuya gider (mod karışmaz).
  - `.` ile başlıyorsa, prefiksi atıp boşluğa göre `args[]` diziye
    böler, eşleşen komutu bulur ve `onCommand(args)`'i çağırır.
  - Komut bulunamazsa kullanıcıya "Unknown command" mesajı basar.
  - Komut çalışırken **exception fırlatırsa yakalanır**, kullanıcıya
    genel bir hata mesajı gösterilir ve stack trace konsola basılır —
    yani hatalı bir komut oyunu çökertmez.
  - Her durumda **`true` döner**, yani `.` ile başlayan hiçbir mesaj
    sunucuya gitmez (bilinmeyen komut olsa bile).

### 6.3. Komutlar tek tek

| Komut | Alias | Kullanım | Ne yapar |
| --- | --- | --- | --- |
| `Help` | `h`, `?` | `.help` | Mod adı+sürümünü ve kayıtlı tüm komutların kullanım/açıklamasını sohbete yazar. |
| `Modules` | `list`, `mods` | `.modules` | Her kategori için, o kategorideki modülleri açık (yeşil) / kapalı (kırmızı) renkte listeler. Boş kategoriler atlanır. |
| `Toggle` | `t` | `.toggle <modül>` | İsimle modül bulur, `toggle()` çağırır, yeni durumu (ON/OFF) bildirir. Modül bulunamazsa hata mesajı. |
| `Bind` | `b` | `.bind <modül> <tuş\|none>` | Modülü bir tuşa bağlar. `none` yazılırsa bağı kaldırır. Tuş adı `InputConstants.getKey("key.keyboard." + ad)` ile çözülür — yani vanilla'nın tuş isimlerini kullanır (`r`, `g`, `f4`, `left.shift` gibi). Geçersiz tuş adı girilirse örnekli bir hata mesajı gösterilir. |
| `Set` | `setting` | `.set <modül> <ayar> <değer>` | Modülün bir ayarını değiştirir. `enabled` ve `keybind` bu komuttan **hariç tutulmuş** (onlar `.toggle`/`.bind` ile yönetiliyor). Ayar bulunamazsa modülün mevcut ayarlarını listeler. Değer tipi mevcut değerin tipine göre otomatik dönüştürülür (Boolean → `parseBoolean`, Number → `parseDouble`, aksi halde ham string). |

### 6.4. `KeybindManager` — tuş algılama

`src/main/java/me/mrhakan/agalarhack/managers/KeybindManager.java`

- Her tick çalışır (bkz. `AgalarHackClient.onInitializeClient`), fakat
  bir **GUI ekranı açıksa** (envanter, chat kutusu, menü vs.) hiçbir şey
  yapmaz — `client.gui.screen() != null` kontrolü (26.x'te `screen`
  alanı `Minecraft`'tan `Gui`'ye taşındı).
- Her modül için `getKey()` ile atanmış tuşu okur; tuş atanmamışsa
  atlar.
- **Basma kenarı (press-edge) algılama**: `lastPressed` map'i ile bir
  önceki tick'te tuşun basılı olup olmadığını hatırlar. Tuş şu an basılı
  ve **önceki tick'te basılı değildi** ise `toggle()` çağrılır. Bu,
  tuşu basılı tutunca modülün sürekli açılıp kapanmasını (spam
  toggle) engelliyor — eski 1.12.2 sürümündeki bilinen hatalardan biri
  buydu, burada düzeltilmiş durumda.

---

## 7. HUD (ekran üstü gösterim)

`src/main/java/me/mrhakan/agalarhack/ui/Hud.java`

`HudElementRegistry`'ye kayıtlı `extractRenderState(GuiGraphicsExtractor, DeltaTracker)`
statik metodu her karede çalışır:

1. Oyuncu yoksa (menüdeyken, dünya yüklenmemişken) hiçbir şey çizmez.
2. **Sol üstte** mod adını gökkuşağı renginde (`rainbow(0)`), yanında
   sürüm numarasını krem renginde yazar.
3. **Sağ üstte**, açık olan modüllerin bir listesini ("arraylist")
   çizer:
   - Önce açık modülleri toplar, `ModuleComparator` ile **isim
     genişliğine göre azalan sırada** (en geniş isim en üstte) sıralar
     — bu, klasik hack client HUD'larındaki "üçgen" görünümünü verir.
   - Her modül ismini sağa hizalı, gökkuşağı renk kaymasıyla
     (`rainbow(counter * 300)` — her satır farklı bir faz'da) çizer.
4. **`rainbow(delay)`**: `System.currentTimeMillis()` tabanlı, HSB renk
   uzayında dönen bir gökkuşağı efekti üretir; `delay` parametresi farklı
   metinlerin faz farkıyla (senkron değil, birbirini takip eder gibi)
   görünmesini sağlıyor.

---

## 8. Kaynak dosyalar

- **`src/main/resources/fabric.mod.json`**: Fabric'in mod tanım dosyası.
  `id`, `name`, `description`, `authors`, `license` (`All-Rights-Reserved`),
  `icon`, `environment: client`, entrypoint ve bağımlılık kısıtları
  (`fabricloader >=0.19.3`, `fabric-api`, `minecraft ~26.2`,
  `java >=25`) burada.
- **`src/main/resources/assets/agalarhack/icon.png`**: mod ikonu (Fabric
  mod listesinde görünen resim).

---

## 9. Build sistemi

### 9.1. `build.gradle`

- `fabric-loom` plugin'i (1.7.4) Minecraft/Fabric bağımlılıklarını ve
  deobfuscation işini yönetiyor.
- `version`/`group`/`archivesName` `gradle.properties`'ten geliyor.
- `dependencies` bloğu: Minecraft jar'ı, Yarn mapping'leri, Fabric Loader,
  Fabric API — hepsi `gradle.properties`'teki sürüm değişkenlerinden.
- `processResources`: `fabric.mod.json` içindeki `${version}` yer
  tutucusunu gerçek sürümle değiştirir.
- Java 25 hedefleniyor (`sourceCompatibility`/`targetCompatibility`/
  `options.release`), ayrıca `withSourcesJar()` ile kaynak jar'ı da
  otomatik üretiliyor (`-sources.jar`).

### 9.2. Gradle wrapper

`gradle/wrapper/gradle-wrapper.properties` → Gradle **9.5.1**. `gradlew`
dosyası executable bit'e sahip (bir önceki değişiklikte düzeltildi).

### 9.3. Build/çalıştırma komutları

```sh
./gradlew build        # mod jar'ını build/libs/ altına üretir
./gradlew runClient     # geliştirme istemcisini başlatır (Loom sağlıyor)
```

---

## 10. CI/CD — GitHub Actions

İki ayrı workflow var, **farklı amaçlarla**:

### 10.1. `CI` — `.github/workflows/build.yml`

- **Tetikleyici**: `main`'e push, `main`'e açılan PR, manuel
  (`workflow_dispatch`).
- **Adımlar**: checkout → JDK 25 kurulumu → Gradle setup → `gradlew`'i
  executable yap → `./gradlew build --stacktrace` → `gradle.properties`
  içinden `mod_version`'ı oku → jar'ları (`*.jar`, sources hariç) artifact
  olarak yükle.
- **Sadece `main`'e push olduğunda** (`github.event_name == 'push' &&
  github.ref == 'refs/heads/main'`) ekstra bir adım çalışır:
  `softprops/action-gh-release@v2` ile bir **"rolling" (sürekli güncellenen)
  prerelease** yayınlanır:
  - `tag_name: ${{ github.run_id }}` — her çalıştırmaya benzersiz bir tag,
    yani her main push'u ayrı bir release oluşturur.
  - `name`: `Rolling build v<mod_version>+<commit_sha>` formatında.
  - `prerelease: true` — resmi `v*` release'lerinden ayırt edilsin diye.
  - `files: build/libs/!(*-sources|*-dev).jar` — sadece asıl mod jar'ı
    (kaynak/dev jar'ları hariç).
- Bu workflow'un `permissions: contents: write` iznine ihtiyacı var
  (release oluşturmak için); YAML'da tanımlı, ayrıca repo ayarlarında da
  "Workflow permissions → Read and write" açık olmalı.

### 10.2. `Release` — `.github/workflows/release.yml`

- **Tetikleyici**: sadece **`v*` deseniyle eşleşen bir tag push
  edildiğinde** (`git tag v26.2 && git push origin v26.2`).
- **Adımlar**: checkout (tam geçmişle, `fetch-depth: 0`) → JDK 25 →
  Gradle setup → build → `softprops/action-gh-release@v2` ile **resmi,
  temiz bir release** yayınlar:
  - `generate_release_notes: true` — GitHub otomatik changelog üretir
    (bir önceki tag'den bu yana olan commit/PR'lardan).
  - `prerelease: false`, `draft: false`.
  - Aynı glob (`!(*-sources|*-dev).jar`) ile sadece asıl jar eklenir.

### 10.3. İki workflow'un farkı — özet

| | `CI` (build.yml) | `Release` (release.yml) |
| --- | --- | --- |
| Tetikleyici | her `main` push/PR | sadece `v*` tag |
| Release sıklığı | her main push'unda | sadece siz tag attığınızda |
| Release tipi | prerelease (rolling) | resmi (stable) release |
| Tag adı | `github.run_id` (rastgele artan sayı) | sizin verdiğiniz `vX.Y` |
| Changelog | yok | otomatik üretilir |

---

## 11. Proje yapısı (dosya ağacı)

```
.
├── .github/workflows/
│   ├── build.yml            # CI + rolling prerelease
│   └── release.yml          # tag-tabanlı resmi release
├── build.gradle              # Loom + bağımlılıklar + build ayarları
├── gradle.properties          # sürüm/mapping sabitleri
├── settings.gradle
├── gradlew / gradlew.bat / gradle/wrapper/
├── README.md                  # kullanıcıya dönük tanıtım/kurulum
├── ACIKLAMA.md                 # bu dosya
└── src/main/
    ├── java/me/mrhakan/agalarhack/
    │   ├── AgalarHackClient.java        # giriş noktası
    │   ├── commands/
    │   │   ├── Command.java             # soyut komut sınıfı
    │   │   └── impl/                    # Help, Modules, Toggle, Bind, Set
    │   ├── managers/
    │   │   ├── CommandManager.java      # sohbet → komut yönlendirme
    │   │   ├── KeybindManager.java      # tuş algılama (press-edge)
    │   │   ├── MessageManager.java      # sohbete mesaj gönderme
    │   │   ├── ModuleManager.java       # modül kayıt defteri + tick
    │   │   ├── Settings.java            # modül başına ayar map'i
    │   │   └── SettingsManager.java     # JSON okuma/yazma
    │   ├── module/
    │   │   ├── Category.java
    │   │   ├── Module.java              # temel sınıf
    │   │   ├── combat/Aura.java
    │   │   ├── movement/{Speed,Flight,Jesus,Sprint,Step,NoFall}.java
    │   │   └── render/Fullbright.java
    │   └── ui/Hud.java                  # ekran üstü gösterim
    └── resources/
        ├── fabric.mod.json
        └── assets/agalarhack/icon.png
```

---

## 12. Şu anki kısıtlar / bilinen eksikler

Bunlar "bug" değil, projenin **şu anki olgunluk seviyesinin** doğal
sonuçları — ileride genişletilebilecek noktalar:

1. **Grafik arayüz (ClickGUI) yok.** Her şey sohbet komutlarıyla
   yönetiliyor; fare ile tıklanabilir bir modül paneli mevcut değil.
2. **`EXPLOITS`, `MISC`, `WORLD` kategorileri boş.** Enum'da tanımlı ama
   hiçbir modül bu kategorilere atanmamış.
3. **Aura'da görüş hattı kontrolü yok** — duvarın arkasındaki hedefe de
   saldırabilir.
4. **NoFall ve Fullbright, sunucu tarafına paket/efekt göndererek
   çalışıyor** — anti-cheat'i güçlü sunucularda tespit edilme riski var
   (bu, tasarım gereği; tamamen client-side bir alternatif için render
   katmanına (mixin) inmek gerekir, ki bu proje şu an mixin
   kullanmıyor).
5. **Mixin kullanılmıyor.** Tüm modüller sadece Fabric API'nin sunduğu
   olaylarla (tick, chat, hud render) sınırlı. Daha derin hook'lar
   (örneğin gerçek gamma override, paket engelleme/manipülasyon) mixin
   gerektirir ve bu projede henüz yok.
6. **Testler yok.** Ne birim test ne entegrasyon testi mevcut; CI sadece
   derlemenin başarılı olduğunu doğruluyor.
7. **Lisans dosyası yok.** Eski Forge MDK lisans metinleri kaldırıldı
   (artık ilgisizdi) ama yerine yeni bir `LICENSE` dosyası eklenmedi;
   `fabric.mod.json` içinde `"license": "All-Rights-Reserved"` yazıyor
   ama repo kökünde bunu destekleyen ayrı bir dosya yok.
8. **Rolling release'ler birikir.** `CI` workflow'u her `main` push'unda
   yeni bir prerelease oluşturduğu için, zamanla Releases sayfasında çok
   sayıda "Rolling build" girişi birikecektir; şu an eskilerini otomatik
   temizleyen bir mekanizma yok.

---

## 13. Branch durumu

- **`claude/modules-commands-v26.2-usu5v6`** (bu dosyanın ait olduğu
  branch): güncel Fabric/26.2 client. Aktif geliştirme burada.
- **`og`**: orijinal 1.12.2/Forge client, olduğu gibi korunuyor,
  geliştirilmiyor — sadece referans/nostalji amaçlı.
- **`main`**: repo'nun varsayılan branch'i; CI/Release workflow'ları
  `main`'e göre tetikleniyor, yani buradaki değişikliklerin gerçek
  anlamda "yayınlanması" için `main`'e merge edilmesi gerekiyor.
