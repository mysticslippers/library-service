import org.flywaydb.core.Flyway;

class InitializeDrill {
    public static void main(String[] args) {
        Flyway.configure()
                .dataSource(System.getenv("DRILL_JDBC_URL"), System.getenv("POSTGRES_USER"),
                        System.getenv("POSTGRES_PASSWORD"))
                .locations("filesystem:" + args[0])
                .load()
                .migrate();
    }
}
