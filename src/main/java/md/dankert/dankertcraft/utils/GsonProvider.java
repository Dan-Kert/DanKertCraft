package md.dankert.dankertcraft.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Провайдер единого экземпляра Gson для всего приложения.
 * Обеспечивает консистентное форматирование JSON и кэширование объекта.
 */
public class GsonProvider {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    /**
     * Получить единый экземпляр Gson для всего приложения
     * @return Конфигурированный Gson объект с pretty printing
     */
    public static Gson getInstance() {
        return GSON;
    }

    /**
     * Альтернатива для обратной совместимости
     * @return Конфигурированный Gson объект
     */
    public static Gson getGson() {
        return GSON;
    }

    // Приватный конструктор для предотвращения инстанцирования
    private GsonProvider() {
    }
}
