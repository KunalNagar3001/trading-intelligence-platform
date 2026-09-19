package com.trading.platform.repository.mongo;

import com.trading.platform.model.mongo.NewsArticle;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewsRepository extends MongoRepository<NewsArticle, String> {

    List<NewsArticle> findAllByOrderByPublishedAtDesc(Pageable pageable);

    List<NewsArticle> findByTaggedSymbolsContainingOrderByPublishedAtDesc(String symbol, Pageable pageable);

    boolean existsByUrl(String url);
}