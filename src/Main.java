import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
            GeohashStorage storage = new GeohashStorage();
            Scanner scanner = new Scanner(System.in);

            // 1. Генерация тестовых данных
            System.out.println("Генерация тестовых меток вокруг Москвы...");
      
            generateTestData(storage, 33.751244, 22.618423, 100);

            // 2. Ввод параметров поиска
            System.out.println("\nВведите параметры поиска:");
            System.out.print("Центральная широта: ");
            double lat = scanner.nextDouble();
            System.out.print("Центральная долгота: ");
            double lon = scanner.nextDouble();
            System.out.print("Радиус поиска (км): ");
            double radius = scanner.nextDouble();

            // 3. Выполнение поиска
            List<Marker> results = storage.searchInRadius(lat, lon, radius, 7);

            // 4. Визуализация результатов
            System.out.println("\nРезультаты поиска:");
            results.forEach(marker ->
                    System.out.printf("%s (%.6f, %.6f) - %.2f км от центра%n",
                            marker.name,
                            marker.lat,
                            marker.lon,
                            GeohashStorage.calculateDistance(lat, lon, marker.lat, marker.lon)
                    )
            );
        }

        private static void generateTestData(GeohashStorage storage, double centerLat, double centerLon, int count) {
            Random random = new Random();
            for (int i = 1; i <= count; i++) {
                // Генерация случайных координат в радиусе 10 км от центра
                double lat = centerLat + (random.nextDouble() - 0.5) * 0.18; // ~10 км
                double lon = centerLon + (random.nextDouble() - 0.5) * 0.36; // ~10 км
                storage.addMarker(new Marker(lat, lon, "Метка #" + i), 7);
            }
        }
    }

    class GeoHashConverter {
        private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";
        private static final int BITS_PER_CHAR = 5;

        /**
         * Кодирование координат в геохаш
         * @param lat - широта
         * @param lon - долгота
         * @param precision - длина геохаша (рекомендуется 7-12 символов)
         */
        public static String encode(double lat, double lon, int precision) {
            double[] latInterval = {-90.0, 90.0};
            double[] lonInterval = {-180.0, 180.0};
            StringBuilder hash = new StringBuilder();
            boolean isEven = true;
            int bit = 0, ch = 0;

            // Итеративное уточнение координат
            for (int i = 0; i < precision * BITS_PER_CHAR; i++) {
                if (isEven) {
                    // Уточнение долготы
                    double mid = (lonInterval[0] + lonInterval[1]) / 2;
                    if (lon > mid) {
                        ch |= (1 << (4 - bit));
                        lonInterval[0] = mid;
                    } else {
                        lonInterval[1] = mid;
                    }
                } else {
                    // Уточнение широты
                    double mid = (latInterval[0] + latInterval[1]) / 2;
                    if (lat > mid) {
                        ch |= (1 << (4 - bit));
                        latInterval[0] = mid;
                    } else {
                        latInterval[1] = mid;
                    }
                }
                isEven = !isEven;

                // Запись символа каждые 5 бит
                if (++bit == BITS_PER_CHAR) {
                    hash.append(BASE32.charAt(ch));
                    bit = 0;
                    ch = 0;
                }
            }
            return hash.toString();
        }
    }

    class GeohashStorage {
        private final Map<String, List<Marker>> storage = new HashMap<>();

        /**
         * Добавление метки в хранилище
         * @param marker - объект метки
         * @param precision - точность геохаша
         */
        public void addMarker(Marker marker, int precision) {
            String geohash = GeoHashConverter.encode(marker.lat, marker.lon, precision);
            storage.computeIfAbsent(geohash, k -> new ArrayList<>()).add(marker);
        }

        /**
         * Поиск меток в радиусе
         * @param centerLat - широта центра
         * @param centerLon - долгота центра
         * @param radiusKm - радиус поиска в километрах
         * @param precision - точность геохаша для поиска
         */
        public List<Marker> searchInRadius(double centerLat, double centerLon, double radiusKm, int precision) {
            // 1. Находим все геохаши, покрывающие зону поиска
            Set<String> coveringHashes = getCoveringGeohashes(centerLat, centerLon, radiusKm, precision);

            // 2. Собираем все метки-кандидаты из этих ячеек
            List<Marker> candidates = coveringHashes.stream()
                    .flatMap(hash -> storage.getOrDefault(hash, Collections.emptyList()).stream())
                    .collect(Collectors.toList());

            // 3. Точная фильтрация по расстоянию
            return candidates.stream()
                    .filter(marker -> calculateDistance(centerLat, centerLon, marker.lat, marker.lon) <= radiusKm)
                    .collect(Collectors.toList());
        }

        /**
         * Получение геохашей, покрывающих зону поиска (упрощенная версия)
         */
        private Set<String> getCoveringGeohashes(double lat, double lon, double radius, int precision) {
            // В реальной реализации нужно рассчитать bounding box и найти все пересекающиеся геохаши
            // Здесь для простоты берем центральный геохаш и его соседей
            String centerHash = GeoHashConverter.encode(lat, lon, precision);
            Set<String> hashes = new HashSet<>();
            hashes.add(centerHash);
            hashes.addAll(getNeighbors(centerHash));
            return hashes;
        }

        /**
         * Расчет расстояния по формуле гаверсинусов
         */
        public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
            final int R = 6371; // Земной радиус в км
            double dLat = Math.toRadians(lat2 - lat1);
            double dLon = Math.toRadians(lon2 - lon1);
            double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                            Math.sin(dLon/2) * Math.sin(dLon/2);
            return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        }

        /**
         * Получение соседних геохашей (заглушка)
         */
        private List<String> getNeighbors(String geohash) {
            return List.of(geohash + "b", geohash + "c", geohash + "d");
        }
    }

    class Marker {
        double lat;
        double lon;
        String name;

        public Marker(double lat, double lon, String name) {
            this.lat = lat;
            this.lon = lon;
            this.name = name;
        }
    }
