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

1. **Overlay aileleri.** `WorldOverlayRenderer` varlık, blok, çizgi ve etiket ailelerine bölünsün.
   Her overlay tek bir arayüzü uygulasın: `collect(context)` ve `geometry(pose, buffer)`. Çağıran
   tek bir döngü olsun, modül adına göre `instanceof` zinciri kalksın.
2. **Ölçülebilir overlay maliyeti.** `RenderService.guard` her overlay için süre toplasın. Var olan
   `ModuleTimingsHud` tick sürelerinin yanında render sürelerini de göstersin.
3. **Trajectories tek varlık sorgusu.** Ölçüm bir maliyet gösterirse yol önce blok çarpışmasıyla
   çıkarılsın. Sonra tüm yolu kapsayan kutuda tek bir varlık sorgusu yapılıp segmentler bu
   adaylarla test edilsin.
4. B tamamlanınca overlay arayüzü addon'lara açılabilir (2.1.00 sonrası karar).

Kabul: bölünme sonrası tüm render game testleri değişmeden geçer. Yeni bir overlay eklemek tek bir
sınıf yazmak ve tek satır kayıt anlamına gelir.

### D. AutoGrind 2 — L

Amaç: [AUTOGRIND.md](AUTOGRIND.md)'deki elle müdahale noktalarını azaltmak. Baritone isteğe bağlı
kalır ve özel pathfinder yazılmaz.

1. **`GrindExecutor`'ın bölünmesi (önce).** Seyahat, istasyon yönetimi, envanter baskısı ve kampanya
   durumu ayrı sınıflara ayrılsın. Her adım davranışı değiştirmeyen ayrı bir commit olsun ve
   ölçütü mevcut game testleri ile gerçek Baritone testi olsun.
2. **Depodan geri alma.** Kampanyanın bir sonraki adımı depolanmış malzemeye ihtiyaç duyuyorsa
   `ContainerTransferController` ile sandıktan alınsın. Kişiye ait (adı değiştirilmiş ya da büyülü)
   eşyalar korunur.
3. **İnşaat alanı seçimi.** Oyuncunun durduğu yerde düz alan yoksa tarayıcı bütçesiyle yakında
   uygun bir alan aransın. Bulunamazsa net bir duraklatma mesajı verilsin.
4. **Avlanmada keşif.** Yüklü hayvan yoksa Baritone ile sınırlı bir keşif yapılsın. Baritone yoksa
   bugünkü duraklatma kalır.
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
2. **Görsel kabul listesi.** GUI ölçekleri, açık/AMOLED/yüksek kontrast temalar, TargetHUD yüz
   katmanı ve ESP/nametag geometrisi için ekran görüntüsü tabanlı, tekrarlanabilir bir kontrol
   listesi yazılsın. İnsan onayı gerektiren kısımlar açıkça işaretlensin.
3. **Dedicated server.** Var olan harness EULA onayı sahibinden alınarak düzenli çalıştırılsın:
   gecikme, yeniden bağlanma ve otomasyon senaryoları.
4. **Kalabalık sunucu bütçeleri.** 2.0.00'daki ortak tarama testi 1.000+ varlığa genişletilsin ve
   blok tarayıcılarla aynı tick'te bütçe paylaşımı ölçülsün.

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
| 2.0.03–2.0.05 | C1–C2 render bölünmesi, D1 GrindExecutor bölünmesi (davranış değişmeden) |
| 2.0.06+ | D2–D4 AutoGrind 2 adımları, C3 (ölçüm sonucuna göre) |
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
