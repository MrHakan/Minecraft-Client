# Agalar Hack — Major Geliştirme Planı (2.1.00)

Bu plan, 2.x.yy şemasındaki ilk büyük sürüm olan **2.1.00**'ın kapsamını tanımlıyor. Kısa vadeli
optimizasyon işleri [GELISTIRME_PLANI.md](GELISTIRME_PLANI.md)'de. Buradaki maddeler daha büyük:
API sözleşmesi, mimari bölünmeler, AutoGrind'ın elle müdahale noktaları ve yayın süreci. Her madde
kod tabanında doğrulanmış bir eksikten çıkıyor, modül sayısını artırmak için yazılmış madde yok.

## Başlangıç noktası (2.0.00)

- 56 yerleşik modül. 2.0.00'da ikisi (PlayerAlerts, SessionTimer) `UNTESTED` idi; 2.0.02'de
  ikisi de gerçek istemcide doğrulandı.
- Addon API sürümü `1` ve [provisional](ADDONS.md). Yayımlanan yüzey yalnızca `AgalarHackApi`,
  `AgalarHackAddon`, `AddonContext`, `Module` ve `Command`. Event bus, HUD kayıt sistemi,
  bildirimler ve tarayıcı bilerek yayımlanmadı.
- En büyük dosyalar `GrindExecutor` (1.650 satır), `SurvivalTasks` (782) ve `WorldOverlayRenderer`
  (747). Yeni bir overlay ya da AutoGrind adımı eklemek bu dosyalara dokunmayı gerektiriyor.
- AutoGrind'ın bilinen elle müdahale noktaları: depolanmış malzemeler geri alınmıyor, ilk Netherite
  yükseltme şablonu oyuncunun bulması gereken bir loot, inşaat için düz ve boş alan gerekiyor,
  avlanma yalnızca yüklü hayvanları hedefliyor.
- CI, `main`'e yapılan **her** push'ta bir release yayımlıyor. Sürüm artırılmazsa aynı "v2.0.00"
  adıyla birden fazla release çıkıyor.

## Sürüm kuralı

- `2.0.yy`: 2.1.00'a kadar olan bakım yayınları (hata düzeltme, ölçülmüş optimizasyon, test).
  Addon API'si yalnızca **eklemeli** değişebilir.
- `2.1.00`: aşağıdaki iş akışları tamamlanınca. Addon API'sinde kırıcı değişikliğe yalnızca burada
  izin var ve `AgalarHackApi.version()` `2` olur.
- Bir iş akışı 2.1.00'a yetişmezse sürüm onu beklemez. İş bir sonraki major'a kayar.

## İş akışları

Boyutlar görecelidir: **S** birkaç gün, **M** bir iki hafta, **L** daha uzun.

### A. Yayın süreci ve sürüm güvenliği — S (A1–A3: 2.0.01'de yapıldı)

Bu iş akışı ilk sırada, çünkü diğer işlerin her biri 2.0.yy olarak çıkacak.

1. ✅ **Aynı sürümle ikinci release çıkmasın.** CI, aynı `mod_version` ile daha önce release
   yapılmışsa yayın adımını atlasın (ya da PR'da `yy` artırılmadıysa uyarsın). Kod değişmeyen
   push'lar (yalnızca doküman) release üretmesin.
2. ✅ **Etiket = sürüm.** Release etiketi CI çalıştırma numarası (`36696095697`) yerine `v2.0.yy`
   olsun. `release.yml` ile `build.yml`'deki iki ayrı yayın yolu tek yola indirilsin.
3. ✅ **CHANGELOG.md.** Her sürüm için kısa bir değişiklik listesi. Release notu buradan alınsın.
4. ⏳ **Güncelleme denetleyicisi sürümü tanısın.** (2.1.00) Şu an commit farkına bakıyor. 2.x.yy sıralaması
   ile "yeni sürüm var" ve "farklı bir derleme" ayrı ayrı gösterilsin. Sıralamayı Fabric zaten
   doğru yapıyor (`2.0.10 > 2.0.09`).

Kabul: art arda iki doküman push'u yeni release üretmez. Sürüm artırılan bir push `v2.0.yy`
etiketli tek bir release üretir ve notu CHANGELOG'dan gelir.

2.0.01'deki uygulama: CI sürüm okuma, CHANGELOG ve etiket kontrolünü işin başında yapıyor.
`tools/release_notes.py` sürümün bölümünü yazdırıyor; bölüm yoksa ya da boşsa PR'ı düşürüyor.
`v<sürüm>` etiketi zaten varsa yayın adımı atlanıyor ve PR'da uyarı çıkıyor. Etiket listesi
okunamazsa iş duruyor, böylece belirsiz bir durumda yinelenen release çıkmıyor. Etiketler
`v2.x.yy` biçiminde ve doğrudan derlenen commit'e konuyor. `release.yml` kaldırıldı.

### B. Addon API 2 — L

Amaç: addon'ların bugün yalnızca modül ve komut ekleyebildiği yüzeyi, en çok istenecek üç alanla
genişletmek ve sürümü kararlı hâle getirmek.

1. **Yaşam döngüsüne bağlı olaylar.** Addon'a ham `EventBus` yerine sahibi belli abonelikler
   verilsin (tick, HUD çizimi, dünya değişimi, sohbet). Addon kaldırılınca ya da hata verince
   abonelik kendiliğinden kapansın. `ModuleGuard`'daki hata yalıtma kuralı aynen geçerli.
2. **HUD widget kaydı.** `HudRegistry` zaten dinamik kayıt ve geç kaydı destekliyor (2.0.00'da
   testi var). Bunun addon'a açık, sınırlı bir sarmalayıcısı yazılsın: kimlik ad alanı
   (`addonid.widget`), boyut bildirimi zorunlu, HUD editöründe görünür.
3. **Bildirim ve ayar türleri.** `NotificationService.publish` ve mevcut tipli ayarlar (sayı,
   seçim, renk, tuş) addon modüllerine resmi olarak açılsın.
4. **Sürüm sözleşmesi.** `AgalarHackApi.version()` = `2`. Addon'lar
   `"depends": { "agalarhack": ">=2.1.00" }` yazabilsin. ADDONS.md'deki örnek buna göre
   güncellensin.
5. **Eksik bağımlılık mesajı.** Şu an manuel kabulde duran "eksik addon bağımlılığı" senaryosu
   `productionAddonInstallationTest`'e üçüncü bir fixture ile eklensin.

Yayımlanmayacaklar: tarayıcı ve zamanlayıcı, rotasyon servisi, envanter kiralama, config
codec'leri. Bunlar hâlâ değişiyor ve addon'a açılırsa donarlar.

Kabul: ayrı paketlenmiş bir fixture addon olay aboneliği ve HUD widget'ı kullanır, hata verdiğinde
yalnızca kendisi kapanır, kaldırıldığında iz bırakmaz. Bunların hepsi üretim jar'ıyla yapılan
kurulum testinde doğrulanır.

### C. Render mimarisi — M

1. ✅ **Overlay aileleri (2.0.03).** `WorldOverlayRenderer` kaldırıldı. Her modülün overlay'i
   `ui/overlay` altında küçük bir `WorldOverlay` sınıfı: `EntityOverlays`, `BlockOverlays` ve
   `PathOverlays` aileleri, çizim yardımcıları `OverlayDraw` içinde. `WorldOverlays` tek bir sıralı
   listeyle etiketleri ve çizgileri dolaşıyor; modül adına göre `instanceof` zinciri kalktı. Metod
   gövdeleri değişmeden taşındı (40 metod otomatik karşılaştırıldı), çizim sırası ve modül başına
   hata yalıtımı aynı, sırayı bir birim testi sabitliyor.
2. ✅ **Ölçülebilir overlay maliyeti (2.0.05).** Kare tabanlı `OverlayTimings` deposu, her
   overlay'in etiket ve çizgi geçişlerini tek bir kare örneği olarak topluyor (120 karelik pencere).
   `ModuleTimingsHud` tick sürelerinin altında en pahalı üç overlay'i gösteriyor. Ölçüm yalnızca
   widget açıkken yapılıyor. Ölçüm `RenderService.guard` yerine `WorldOverlays`'te yapılıyor, çünkü
   karenin hangi overlay'e ait olduğunu yalnızca orası biliyor. Game testi, 300 varlık görünürken beş
   varlık overlay'inin de ölçüldüğünü doğruluyor.
3. ✅ **Trajectories tek varlık sorgusu (2.0.10'da ölçülüp kapatıldı).** Ölçüm bir maliyet gösterirse yol önce blok çarpışmasıyla
   çıkarılsın. Sonra tüm yolu kapsayan kutuda tek bir varlık sorgusu yapılıp segmentler bu
   adaylarla test edilsin.
   İlk ölçüm (2.0.05 game testi, yazılım rasterleştiricisi, 300 zırh askısı görünür):
   ESP 1812 µs/kare (256 kutu), Nametags 196, Tracers 53, ItemESP 11, ProjectileESP 8.
   Trajectories bu senaryoda ölçülmedi. Sıradaki render işi, Trajectories'ten önce ESP'nin kutu
   başına maliyeti olmalı.
   ✅ **ESP maliyeti (2.0.09).** `OverlayTimings` artık her overlay'in etiket ve çizgi geçişini ayrı
   da kaydediyor (`slowestParts`). Ayrık ölçüm etiketlerin büyük olduğunu gösterdi: 256 hedef
   görünürken etiket ~200 µs, kutu ~150 µs; hedefler oyuncunun arkasındayken de aynı, çünkü iki geçiş
   de görüş alanına bakmıyordu. Etiketler ve kutular artık görüş alanı dışındaki hedefleri atlıyor
   (tracer'lar hiçbir zaman atlanmıyor), etiketteki mesafe ve can `String.format` olmadan yazılıyor
   (metin birebir aynı, birim testi 160.000 değerle karşılaştırıyor). Aynı sahnede yerel ölçüm, beş
   120 karelik pencere: hedeflere bakarken ESP 354 → 290 µs (etiket 201 → 161, kutu 152 → 129),
   arkası dönükken 361 → 52 µs. Kutu başına normalleri eksen başına bir kez hesaplayan bir yazıcı
   denendi; çıktı aynı ama kazanç ölçülemedi, maliyet vertex yazımında olduğu için bırakıldı.
   Game testi artık ölçümden önce hedeflere dönüyor ve görüş kırpmasını doğruluyor (önde etiket ve
   kutu var; arkada hiç yok, 256 tracer'ın hepsi çiziliyor).
   **C3 ölçümü (2.0.10).** Aynı sahnede kartopu ile: açık alanda ~45 µs/kare, kalabalığa doğru
   20–28 µs, en kötü durumda (dümdüz yukarı, 300 adım) 70–80 µs. Bu, görüşteki 256 hedefli ESP'nin
   dörtte biri ve bir karenin %0,5'inden az; tek sorguya geçiş kendini ödemez. Madde değişiklik
   yapılmadan kapatıldı.
4. B tamamlanınca overlay arayüzü addon'lara açılabilir (2.1.00 sonrası karar).

Kabul: bölünme sonrası tüm render game testleri değişmeden geçer. Yeni bir overlay eklemek tek bir
sınıf yazmak ve tek satır kayıt anlamına gelir.

### D. AutoGrind 2 — L

Amaç: [AUTOGRIND.md](AUTOGRIND.md)'deki elle müdahale noktalarını azaltmak. Baritone isteğe bağlı
kalır ve özel pathfinder yazılmaz.

1. **`GrindExecutor`'ın bölünmesi (önce).** Seyahat, istasyon yönetimi, envanter baskısı ve kampanya
   durumu ayrı sınıflara ayrılsın. Her adım davranışı değiştirmeyen ayrı bir commit olsun ve
   ölçütü mevcut game testleri ile gerçek Baritone testi olsun.
   - ✅ 2.0.04: altı görev sınıfı (toplama, offhand, istasyon yerleştirme/açma, üretim, eritme)
     ayrı dosyalara taşındı. `GrindExecutor` 1.650 satırdan 814 satıra indi. Taşıma derleyici
     rehberliğinde yapıldı ve taşınan 788 satır, eklenen `g.` önekleri dışında orijinalle aynı.
   - ✅ 2.0.05: seyahat (`GrindTravel`), istasyon kayıtları (`GrindStations`) ve erişim/görüş/nişan
     yardımcıları (`GrindReach`) ayrıldı; `GrindExecutor` 592 satır. Aynı derleyici rehberli
     yöntemle taşınan 210 satır, yürütücü öneki dışında orijinalle aynı.
   - ✅ 2.0.06: envanter yardımcıları (`GrindItems`: sayım, hotbar/alet hazırlığı, üretim ızgarası
     tıklamaları) ayrıldı; `GrindExecutor` 341 satır. Taşınan 225 satır yürütücü öneki dışında aynı.
2. ✅ **Depodan geri alma (2.0.06).** Taşınanlarla yapılamayan bir hedef, toplamaya geçmeden önce
   tarif zincirinin bir kısmını tutan depo sandıklarına uğruyor (Baritone ile 64 blok, yoksa erişim
   mesafesi). Zincirin her basamağında önce taşınan, sonra depodaki stok harcanıyor; böylece depodaki
   külçe, taşınan cevheri eritmeye tercih ediliyor. Adı değiştirilmiş, büyülü ve kırılmak üzere olan
   eşyalar alınmıyor, iki boş yuva korunuyor, ulaşılamayan sandık kampanyayı duraklatmıyor.
   Kampanya deposu ve içerik kaydı `GrindStorage`'da.
3. ✅ **İnşaat alanı seçimi (2.0.07).** Kampanya yeni bir dünyada başlarken, kademenin bütün
   binalarının (ev, depo eki, portal, büyü alanı) taban alanına uyan en yakın temiz ve düz alanı
   16 blok içinde, yalnızca yüklü chunk'larda bir kez arıyor. Her sütunun üst bloğu bir kez okunuyor
   ve sonuç tekrar kullanılıyor; arama süresi günlüğe yazılıyor (game testlerinde en fazla 10 ms).
   Düz zeminde konum eskisiyle aynı.
   Alan yoksa kampanya baştan, net bir mesajla duraklıyor; `.grind resume` varsayılan köşeyi kabul
   ediyor. Not: "tarayıcı bütçesi" yerine tek seferlik, sınırlı bir okuma seçildi (en fazla birkaç
   bin sütun), çünkü kampanyanın görev listesi tabanın konumu belli olmadan kurulamıyor.
4. ✅ **Avlanmada keşif (2.0.08).** 64 blok içinde uygun hayvan yoksa ve Baritone varsa av, çıktığı
   yerin çevresinde sabit bir gözcü turu yürüyor: 48 bloklık halkada sekiz, 96 bloklık halkada sekiz
   nokta, doğudan saat yönünde. Görüşe giren ilk uygun hayvan avlanıyor; ulaşılamayan nokta atlanıyor,
   tur bitince (yaklaşık 160 blok) net bir mesajla duraklıyor. Baritone yoksa bugünkü duraklatma
   aynen kalıyor. Gerçek Baritone testi, görüş mesafesi dört chunk'ken 100 blok ötede, istemcinin
   yüklemediği tek bir inekten et alıyor.
5. **İlk şablon.** Bastion bulma ve yağmalama güvenilir biçimde test edilemiyor. Bu madde ancak
   gerçek bir test fixture'ı kurulabilirse ele alınır, aksi hâlde belgelenmiş sınır olarak kalır.

Kabul: test dünyasında depolama gerektiren bir kampanya adımı elle müdahale olmadan tamamlanır. Bir
düz alan senaryosu engebeli bir başlangıçtan alan bulur.

### E. Kanıt ve kabul — M

1. ✅ **`UNTESTED` rozetlerini kaldırmak (2.0.02).** PlayerAlerts için entegre sunucuda bir
   Fabric `FakePlayer` kullanıldı. Önce oyuncu listesi bilgisi gönderiliyor, sonra oyuncu dünyaya
   ekleniyor. İstemci onu ağ üzerinden gerçek bir oyuncu gibi alıyor. Senaryo giriş ve çıkış
   bildirimlerini doğruluyor. SessionTimer test içinde ölçülen süreyle karşılaştırılıyor (3 sn →
   `00:00:03`), yeniden açılınca sıfırlanıyor ve `showSeconds` ayarına uyuyor. Artık `UNTESTED`
   modül yok.
2. ✅ **Görsel kabul listesi (2.0.12).** GUI ölçekleri, açık/AMOLED/yüksek kontrast temalar, TargetHUD yüz
   katmanı ve ESP/nametag geometrisi için ekran görüntüsü tabanlı, tekrarlanabilir bir kontrol
   listesi yazılsın. İnsan onayı gerektiren kısımlar açıkça işaretlensin.
   Sonuç: [VISUAL_ACCEPTANCE.md](VISUAL_ACCEPTANCE.md) ve `VisualAcceptanceGameTest`. Otomatik
   kısım: dört tema × 1–4 GUI ölçeklerinde (pencere bunun için 1280×960'a büyütülüyor) ClickGUI (pencere dışına taşan widget
   yok, temanın vurgu rengi ekranda, Light en parlak), ESP kutusunun hedefin kamera üzerinden
   izdüşen sınır kutusuyla örtüşmesi (1–3 px fark), ESP etiketi ve Nametag'in kutunun üstünde ve
   ortalı olması, TargetHUD yüz bölgesinde gerçek bir deri. Her kare CI'da `visual-acceptance`
   artifact'ı olarak yükleniyor; okunabilirlik, şapka katmanı ve genel görünüm "İnsan onayı" olarak
   işaretli. İlk bulgular: Light temada arama kutusu siyah kalıyor; 4× ölçekte ClickGUI'nin hızlı
   eylem düğmelerinin etiketleri kesiliyor ve modül satırlarında açma/kapama düğmesi adın üstüne
   biniyor.
3. **Dedicated server.** Var olan harness EULA onayı sahibinden alınarak düzenli çalıştırılsın:
   gecikme, yeniden bağlanma ve otomasyon senaryoları.
4. ✅ **Kalabalık sunucu bütçeleri (2.0.10).** 2.0.00'daki ortak tarama testi 1.000+ varlığa genişletilsin ve
   blok tarayıcılarla aynı tick'te bütçe paylaşımı ölçülsün.
   Sonuç (`crowdBudgets` game testi): BlockESP'nin 12 cevheri bulma süresi kalabalık yokken,
   1.500 ve 5.000 varlıkla aynı (27 tick), çünkü varlık ve blok ayrı bütçeler. Tarayıcının tick
   maliyeti 0,25 → 0,63 → 1,28 ms; 4.096'lık gözlem tavanının üstünde tick tam olarak tavanı
   harcıyor. Ölçümün gösterdiği sorun: tarama render listesini geliş sırasıyla okuyor; en yakın 500
   varlık en son geldiğinde ESP'nin 256 hedefinin hiçbiri gerçekten en yakın 256 değil.
5. ✅ **En yakından başlayan varlık taraması (2.0.11).** Tavanın üstünde tarama, varlıkları oyuncuya uzaklık
   sırasıyla (bölüm bölüm) gezsin; böylece ilk 4.096 gözlem en yakın varlıklar olur. Kabul:
   `crowdBudgets` senaryosunda en yakın 500 en son gelse de ESP'nin 256 hedefinin hepsi gerçek en
   yakın 256 olur, tick başına varlık birimi tavanı aşmaz, 1.500 varlıkta sonuç değişmez.
   Sonuç: tavanın üstünde tarama artık `NearestFirstOrder` sırasıyla okuyor. Her varlığın karesel
   uzaklığı tek geçişte 256 eşit banda ayrılıyor (kök yok, sayma sıralaması), en uzak abonenin
   menzili dışındakiler baştan çıkarılıyor. Aynı senaryoda ESP'nin 256 hedefinin 256'sı gerçek en
   yakın (önce 0). Sıralama 5.000 varlıkta tarayıcının tick'ine ~0,3 ms ekliyor (1,28 → 1,55 ms);
   tavanın altında davranış ve ölçüm değişmedi.

### F. Kod sağlığı — S, sürekli

- Dokunulan dosyalarda tam nitelikli adlar `import`'a çevrilsin. Toplu biçimlendirme commit'i
  yapılmaz.
- ✅ `KeybindManager` her tick her modülün tuş metnini yeniden ayrıştırıyordu. 2.0.02'de
  `Module.getChord` tuş ya da değiştirici ayarı değişene kadar sonucu saklıyor.

## Sıra ve bağımlılıklar

```
A (yayın süreci) ─┬─> 2.0.yy bakım yayınları
                  │
F (sürekli) ──────┤
                  │
C1-C2 (render bölünmesi) ──> C3 (Trajectories, ölçüme bağlı)
                  │
D1 (GrindExecutor bölünmesi) ──> D2-D4 (AutoGrind 2)
                  │
B (Addon API 2) ───┴─> 2.1.00
E (kanıt) her adımda paralel
```

Önerilen ara sürümler:

| Sürüm | İçerik |
| --- | --- |
| 2.0.01 | A1–A3: tekrarsız release, `v` etiketleri, CHANGELOG |
| 2.0.02 | F, E1: `UNTESTED` rozetlerinin kaldırılması |
| 2.0.03 | ✅ C1 render bölünmesi (davranış değişmeden) |
| 2.0.04 | ✅ D1 ilk adım: AutoGrind görev sınıfları ayrıldı (davranış değişmeden) |
| 2.0.05 | ✅ D1 devamı (seyahat, istasyon, erişim yardımcıları) ve C2 overlay kare süreleri |
| 2.0.06 | ✅ D1 son adım (envanter yardımcıları) ve D2 depodan geri alma |
| 2.0.07 | ✅ D3 inşaat alanı seçimi |
| 2.0.08 | ✅ D4 avlanmada keşif |
| 2.0.09 | ✅ ESP etiket ve kutu maliyeti (görüş kırpması, `String.format`'sız etiket) |
| 2.0.10 | ✅ E4 kalabalık bütçe ölçümü, C3 ölçülüp kapatıldı |
| 2.0.11 | ✅ E5 en yakından başlayan varlık taraması |
| 2.0.12 | ✅ E2 görsel kabul listesi |
| 2.0.13+ | D5 (karar bekliyor), E3 (EULA kararı bekliyor), sonra 2.1.00 |
| 2.1.00 | B: Addon API 2 ve sürüm sözleşmesi, A4 güncelleme denetleyicisi |

## Riskler

| Risk | Önlem |
| --- | --- |
| Bölünmeler AutoGrind ya da render davranışını sessizce değiştirir | Her adım ayrı commit. Ölçüt mevcut game testleri ve gerçek Baritone testi. Bölünme ile davranış değişikliği aynı commit'e girmez. |
| Addon API 2 erken donar | Yalnızca B'de sayılan üç alan açılır. Geri kalanı istek geldikçe 2.2'de değerlendirilir. |
| Minecraft 26.3 çıkar ve port gerekir | Mod sürümü Minecraft'tan bağımsız. Port bir 2.0.yy ya da 2.1.yy işi olur. Mixin doğrulama ve game testleri ölçüttür. |
| CI süresi uzar (şu an ~16 dk) | Yeni senaryolar mevcut dünyaları paylaşır. Kalabalık sahne testi yazılımsal render'da ucuz kalacak şekilde görünmez varlık kullanır. |

## Kapsam dışı (değişmedi)

- Paket flood, crash/dupe exploit'leri, malformed paket hileleri, anti-cheat bypass presetleri,
  sunucu kontrollerini atlatmaya yönelik gizli rotasyonlar.
- Özel pathfinder. Yalnızca doğrulanmış, uyumlu bir Baritone köprülenir.
- Ayar açıklamalarının Türkçe yerelleştirilmesi (iptal edildi, yeniden başlatılmayacak).
- Yalnızca modül sayısını artırmak için eklenen modüller.
