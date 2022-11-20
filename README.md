приложение с метриками onсall и подсчетом SLA

### метрики
- user_create_error_total - количество ошибок при создании пользователя
- user_delete_error_total - количество ошибок при удалении пользователя
- requests_total - количество всего выполенных запросов
- user_creating_time_seconds_max - максимальное время выполнения запроса на создание пользователя
- user_deletion_time_seconds_max - максимальное время выполнения запроса на удалении пользователя

### соглашение:
- процент успешных запросов должен быть не менее 95
- максимальное время выполнения запроса на создание пользователя 0.3 с
- максимальное время выполнения запроса на удаление 0.3 с

так же в этом приложении считывается выполнение соглашение
для этого были заведены две метрики
- availability - процентная доступность onCall (процент запросов выполненных пробером без ошибок)
- bad_minutes - время, когда запросы превышали максимальное время выполнения
