package config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import service.*;
import app.Main; // <-- Now this import will work!

@Configuration
public class BudgetAppConfig {
    private static final Logger logger = LoggerFactory.getLogger(BudgetAppConfig.class);

    @Bean
    public LocalCacheService localCacheService() {
        return new LocalCacheService();
    }

    @Bean
    public BudgetFileService budgetFileService() {
        logger.info("Creating BudgetFileService bean with path: {}", Main.runtimeCsvPath);
        return new BudgetFileService(Main.runtimeCsvPath);
    }

    @Bean
    public ProjectedFileService projectedFileService() {
        logger.info("Creating ProjectedFileService bean with path: {}", Main.runtimeProjectedCsvPath);
        return new ProjectedFileService(Main.runtimeProjectedCsvPath);
    }

    @Bean
    public ImportService importService() {
        return new ImportService();
    }

    @Bean
    public DigitalOceanWorkspaceService digitalOceanWorkspaceService() {
        return new DigitalOceanWorkspaceService();
    }

    @Bean
    public CSVStateService csvStateService(
            BudgetFileService budgetFileService,
            ProjectedFileService projectedFileService,
            LocalCacheService localCacheService,
            DigitalOceanWorkspaceService digitalOceanWorkspaceService
    ) {
        return new CSVStateService(
                budgetFileService,
                projectedFileService,
                localCacheService,
                digitalOceanWorkspaceService
        );
    }
}