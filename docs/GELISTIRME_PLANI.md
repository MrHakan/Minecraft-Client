# Agalar Hack — Geliştirme ve Optimizasyon Planı

Bu plan, 26.2.7 sürümündeki kod tabanının (`main`, `4da9bb5`) incelenmesiyle hazırlandı. Yeni
modül ekleyip kataloğu büyütmek yerine, oyuncunun hissettiği iki şeye odaklanıyor: **doğru
çalışma** ve **her karede (frame) yapılan gereksiz işin azaltılması**. Kuralların çoğu
[RELEASE_PLAN.md](RELEASE_PLAN.md) içindeki kanıt kapılarından geliyor: önce ölç, sınırlı (bounded)
işi koru, render callback'lerini ucuz tut, kalıcı veriyi güvenli tut.

## Başlangıç durumu

- Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.157.0+26.2, Java 25.
- 56 yerleşik modül, 380 Java dosyası (~25.700 satır `src/main`).
- Java 25 ile `./gradlew test`: **871 test, 0 hata**.
- Açık PR yok (PR #13 kapatıldı: `.grind status` komutunun zaten verdiği bilgiyi HUD'a ekliyordu).

## İncelemede bulunan sorunlar

| # | Tür | Yer | Sorun |
| --- | --- | --- | --- |
| 1 | Hata | `AgalarHackClient.VERSION` | Sürüm `"26.2.5"` olarak elle yazılmış. 26.2.7 yüklüyken HUD markası, Control Center başlığı ve güncelleme kontrolünün `User-Agent` başlığı yanlış sürümü gösteriyor. |
| 2 | Performans | `HudLayoutManager.get()` | Her çağrı `ensureDefaults()` çalıştırıyor. Bu da `putIfAbsent(id, state.copy())` ile **her varsayılan widget için yeni bir nesne** oluşturuyor (kopya, anahtar zaten varken de üretiliyor). `get`/`resolveX`/`resolveY` her karede onlarca kez çağrıldığı için kare başına binlerce gereksiz nesne çıkıyor. |
| 3 | Performans | `HudRegistry.ids()` / `render()` | Widget sırası her karede `stream().sorted(...).toList()` ile yeniden hesaplanıyor. Karşılaştırıcı her adımda `layout.get()` çağırdığı için 2 numaralı sorunu katlıyor. |
| 4 | Performans | `FriendManager.isFriend()` | Her çağrıda listedeki **her arkadaşın adı** yeniden küçük harfe çevriliyor (O(n) + n adet string). ESP rengi, ESP etiketi, Nametags, TargetHUD ve hedef seçimi bunu oyuncu başına, kare başına çağırıyor. |
| 5 | Performans | `WorldOverlayRenderer` (StorageESP / BlockESP) | Her blok için her karede `BuiltInRegistries...getKey(...).toString()` ile yeni bir id string'i üretiliyor. Mesafe iki kez hesaplanıyor, aynı ayar döngü içinde tekrar tekrar okunuyor. |
| 6 | Performans | `Hud.rainbow()` | Module List'te her satır için her karede bir `java.awt.Color` nesnesi ve bir servis `Optional`'ı oluşturuluyor. |
| 7 | Performans | `WorldOverlayRenderer` (ESP) | `entityEspColor` / `espFadeFactor` her varlık için ~12 ayarı yeniden okuyor; bu değerler kare boyunca değişmiyor. |
| 8 | Performans | `ViewCulling.isVisible(BlockPos)` | Her blok testi için yeni bir `AABB` oluşturuluyor. |
| 9 | Performans | Metin HUD widget'ları | Metin üreticisi bir kez ölçüm, bir kez çizim için çalışıyor. `String.format` aynı karede iki kez çalışıyor. |
| 10 | Kapasite | `EntityDiscovery` | Beş ESP tüketicisi aynı varlık bütçesini ayrı ayrı yürüyor. Kalabalık sunucuda (~800+ varlık) birbirlerini kesiyorlar. Tek ortak yürüyüş bu tavanı kaldırır. |
| 11 | Kod sağlığı | `GrindExecutor` (1.650 satır), `SurvivalTasks` (782), `WorldOverlayRenderer` (747) | Tek dosyada çok fazla sorumluluk var. Değişiklik riski yüksek ve okunması zor. |
| 12 | Kod sağlığı | Genel | Tam nitelikli sınıf adları (`me.mrhakan.agalarhack.services.X`) satır içinde yaygın, bazı dosyalarda boşluksuz yoğun biçim var. |
| 13 | Hata (UX) | `Hud` — Info, Target HUD, Movement Stats | HUD editörü bu widget'ların kutusunu, kenar/merkez yakalamasını ve çakışma uyarısını sabit tahminlerle (ör. 150×48) hesaplıyor. İki satırlık bir Info bloğu 48 px yüksekliğinde gösteriliyor. |
| 14 | Performans | `ServiceRegistry.require()` | Her çağrıda bir `Optional` ve değer yakalayan bir lambda oluşturuluyor. Render ve tick kodu bunu her karede çağırıyor. |
| 15 | Performans | ItemESP / InventoryCleaner | Liste filtresi her tick, her eşya için yeni bir id string'i üretiyor. |

## Fazlar

### Faz 1 — Doğruluk ve sıcak yol ✅ tamamlandı

Hedef: oyuncuya görünen hatayı düzeltmek ve her karede çalışan HUD/render kodundaki gereksiz nesne
üretimini kaldırmak. Davranış değişmeyecek; her madde birim testiyle korunacak.

1. **Sürüm tek kaynaktan.** `VERSION`, `fabric.mod.json` üzerinden `gradle.properties` içindeki
   `mod_version` değerinden okunur. Okunamazsa `"dev"` kullanılır. (#1)
2. **`HudLayoutManager.get()` nesne üretmez.** Varsayılan yalnızca widget gerçekten eksikse
   kopyalanır, `ensureDefaults()` da yalnızca eksik anahtar için kopya yapar. (#2)
3. **HUD sırası önbellekte.** Sıra, kayıt ya da z-order değişince yeniden hesaplanır. Sabit
   durumda kare başına sıralama ve liste üretimi yapılmaz. Eşit z-order'da kayıt sırası korunur. (#3)
4. **Arkadaş araması indeksli.** Adlar küçük harfli anahtarla bir `Map` içinde tutulur ve arama O(1)
   yapılır. Görünen adlar, sıra ve dosya biçimi aynı kalır. (#4)
5. **Blok id önbelleği ve döngü dışı ayarlar.** StorageESP ve BlockESP blok başına string
   üretmez. Menzil ve alfa ayarları döngü dışında bir kez okunur, mesafe bir kez hesaplanır. (#5)
6. **Gökkuşağı rengi nesnesiz.** `Color.HSBtoRGB` statik çağrısı kullanılır ve tema bir kez
   çözülür. (#6)
7. **Servis araması nesnesiz.** `ServiceRegistry.require()` doğrudan map araması yapar. (#14)

Kanıt: `./gradlew build` (birim testler + gametest derlemesi) ve `tools/smoke-client.sh` ile
gerçek istemcide game testleri.

### Faz 2 — Render yolunun kalanı (kısmen tamamlandı)

1. ✅ ESP stili (renkler, alfa, solma eşikleri, arkadaş/takım ayarları) varlık başına değil, geçiş
   başına bir kez çözülüyor. (#7)
2. ✅ Info, Target HUD ve Movement Stats editöre son çizilen boyutlarını bildiriyor. Module List
   bunu zaten yapıyordu. (#13)
3. ✅ ItemESP ve InventoryCleaner eşya id'lerini aynı oturum önbelleğinden alıyor. (#15)
4. ❎ `ViewCulling` blok testi (#8): `AABB` değiştirilemez bir sınıf ve `Frustum` yalnızca kutu
   kabul ediyor. Access widener olmadan nesnesiz bir yol yok. Bu kısa ömürlü nesneleri JIT'in
   escape analizi büyük ölçüde siliyor. Ölçüm bir sorun göstermedikçe dokunulmayacak.
5. ❎ Metin widget'larında çift değerlendirme (#9): Ölçüm yalnızca HUD editörü açıkken yapılıyor.
   Oyun sırasında metin kare başına bir kez üretiliyor, yani gerçek bir sıcak yol değil.
6. ⏳ Trajectories simülasyonu adım başına bir varlık sorgusu yapıyor (100 adımda kare başına 100
   sorgu). Olası çözüm: önce blok çarpışmasıyla yolu çıkarmak, sonra tüm yolu kapsayan kutuda tek
   bir varlık sorgusu yapıp segmentleri bu adaylarla test etmek. Çarpışma doğruluğu game
   testleriyle korunmalı. Önce ölçülecek.
7. Her adımdan önce Performance/debug HUD ile ölçüm yapılır. Ölçülemeyen bir kazanç için kod
   eklenmez.

### Faz 3 — Ölçek ✅ tamamlandı

1. ✅ `SharedEntityWalk`: ESP, Tracers, Nametags, ItemESP ve ProjectileESP tick başına tek bir
   varlık taramasından besleniyor. Bir varlık, kaç modül bakarsa baksın bir bütçe birimi tutuyor.
   Modül başına menzil, sonuç sınırı, filtre, dünya değişiminde boşaltma ve 4096 gözlem tavanı
   korunuyor. Hata veren bir filtre yalnızca kendi modülünü kapatıyor. (#10)
2. ✅ `ModuleBehaviourGameTest.sharedEntityWalk`: 300 görünmez zırh standı ve beş overlay açıkken
   tarayıcı sayacı 301 varlık için 301 birim gösterdi (beş ayrı tarama ~1.505 harcardı). ESP 256,
   Nametags 64 sonucunu tam aldı.

### Faz 4 — Kod sağlığı

1. `GrindExecutor`'ı mevcut servis sınırları boyunca bölmek (seyahat, istasyon yönetimi,
   envanter baskısı, kampanya durumu). `TaskRunner` sürdürülebilirliği ve sahiplik kuralları
   korunur. Bölme yalnızca davranışı değiştirmeyen, testle güvenceye alınmış adımlarla yapılır. (#11)
2. `WorldOverlayRenderer`'ı overlay ailelerine ayırmak (varlık, blok, çizgi, etiket). (#11)
3. Dokunulan dosyalarda tam nitelikli adları `import`'a çevirmek. Toplu biçimlendirme commit'i
   yapılmaz, yalnızca değişen kod düzeltilir. (#12)

### Faz 5 — Kabul (manuel)

[RELEASE_PLAN.md](RELEASE_PLAN.md) içindeki manuel maddeler geçerli: farklı GUI ölçeklerinde ve
temalarda görsel kontrol, doğal balık tutma, gecikmeli çok oyunculu sunucu, dedicated server
(EULA yalnızca sahibin onayıyla), eksik addon bağımlılığı mesajı.

## Doğrulama (yerel, Java 25 + Xvfb)

- `./gradlew build`: 908 birim testi, 0 hata (başlangıçta 871).
- `tools/smoke-client.sh`: `main` (`4da9bb5`), ara commit (`354b871`) ve dalın son hâli (`01cdd90`)
  üzerinde geçti. Tüm modül senaryoları, mixin doğrulaması ve log taraması dahil.
- `./gradlew productionAddonInstallationTest`: üretim jar'ı ile addon kurulumu, yeniden başlatma
  ve kaldırma geçti.
- `tools/test-baritone.sh`: gerçek Baritone 1.19.0 ile 384 blok yolculuk, uzak madencilik, alet
  yenileme, yüklenmemiş üsse dönüş, kapı/sandık erişimi ve hedef sahipliği geçti.
- Dedicated server senaryoları EULA onayı gerektirdiği için çalıştırılmadı.

Bir sonraki büyük sürüm (2.1.00) için plan: [MAJOR_GELISTIRME_PLANI.md](MAJOR_GELISTIRME_PLANI.md).

## Sürümleme

Sürümler artık **2.x.yy** biçiminde. `x` büyük (major) sürüm, `yy` iki haneli küçük güncelleme
(00–99). Her normal yayında `yy` artar. Büyük bir güncellemede `x` artar ve `yy` `00`'a döner.
Örnek: `2.0.00` → `2.0.01` → … → `2.1.00`. Bu çalışma `2.0.00`, yani yeni şemadaki ilk sürüm.
Minecraft sürümü (26.2) bu numaradan bağımsız. `build.gradle` başka bir biçimdeki `mod_version`'ı
reddediyor. HUD'da da iki haneli biçim korunuyor (`agalarhack:version` özel alanı).

## Kurallar ve kapsam dışı

- Her faz ayrı ve incelenebilir commit'lerle ilerler. Her commit `./gradlew build` ile yeşil olmalı.
- Ölçülmüş bir sıcak yol olmadan mikro optimizasyon yapılmaz. Önbellekler; blok güncellemesi,
  chunk boşaltma, boyut/dünya değişimi, oyuncu değişimi ve ayar değişimiyle geçersiz kılınmalı.
- Kapsam dışı olanlar değişmedi: paket flood, crash/dupe exploit'leri, anti-cheat bypass
  presetleri ve gizli rotasyonlar eklenmez.

## Durum

| Faz | Durum |
| --- | --- |
| Faz 1 | Tamamlandı |
| Faz 2 | Kısmen: 3 madde yapıldı, 2 madde gerekçesiyle elendi, Trajectories ölçüme bağlı |
| Faz 3 | Tamamlandı |
| Faz 4 | Planlandı |
| Faz 5 | Manuel, sahibin testine bağlı |
