package service;

import java.util.List;

public interface CSVFileService<T> {
    List<T> readAll();

    void add(T row);

    boolean update(String key, String value, T updatedRow);

    boolean delete(String key, String value);

    List<String> getHeaders();
}