package service;

import org.springframework.stereotype.Service;

import java.util.List;


@Service
public interface CSVFileService<T> {
    List<T> readAll();

    void add(T row);

    boolean update(String key, String value, T updatedRow);

    boolean delete(String key, String value);

    List<String> getHeaders();
}