#include "micro_transformer.h"

#include <iostream>
#include <chrono>
#include <iomanip>
#include <vector>
#include <algorithm>

using namespace micro_transformer;

struct EvalItem {
    std::string input;
    std::vector<std::string> expected;
};

int main(int argc, char** argv) {
    std::string modelPath = "/home/rot/Proyectos/simple-keyboard-dictionaries/dictionaries/transformer_es.bin";
    if (argc > 1) {
        modelPath = argv[1];
    }

    MicroTransformerModelCpp model;
    auto t0 = std::chrono::high_resolution_clock::now();
    bool loaded = model.loadModel(modelPath);
    auto t1 = std::chrono::high_resolution_clock::now();

    if (!loaded) {
        std::cerr << "Failed to load model from: " << modelPath << std::endl;
        return 1;
    }

    double loadMs = std::chrono::duration<double, std::milli>(t1 - t0).count();
    std::cout << "C++ Model loaded in " << std::fixed << std::setprecision(2) << loadMs
              << " ms (Vocab: " << model.getVocabSize()
              << ", D: " << model.getModelDim()
              << ", Layers: " << model.getNumLayers()
              << ", Heads: " << model.getNumHeads() << ")" << std::endl;

    std::vector<EvalItem> items = {
        {"buenos", {"días", "dias", "tardes"}},
        {"muchas", {"gracias"}},
        {"por", {"favor", "eso", "cierto", "supuesto", "fa"}},
        {"de", {"nada", "acuerdo", "nuevo", "hecho", "una"}},
        {"hola", {"cómo", "como", "buenas", "amigo", "bro", "todo"}},
        {"cómo", {"estás", "estas", "va", "te"}},
        {"el", {"día", "dia", "tiempo", "mundo", "tema", "problema"}},
        {"la", {"verdad", "casa", "vida", "gente", "posta"}},
        {"los", {"chicos", "días", "dias", "amigos"}},
        {"las", {"cosas", "horas", "palabras"}},
        {"te", {"quiero", "amo", "aviso", "mando", "paso"}},
        {"nos", {"vemos", "hablamos", "vamos", "juntamos"}},
        {"me", {"parece", "voy", "gusta", "fui", "sirve"}},
        {"jaja", {"ja", "jaja", "jajaja", "sí", "si"}},
        {"xq", {"no", "sí", "si", "te"}},
        {"tqm", {"mucho", "amiga"}},
        {"todo", {"bien", "mal", "tranqui", "joya"}},
        {"nada", {"que", "de", "más", "mas"}},
        {"che", {"boludo", "amigo", "joya", "todo", "qué"}},
        {"tenés", {"que", "un", "una", "tiempo", "ganas", "el"}},
        {"podés", {"pasar", "venir", "hacer", "mandar"}},
        {"dale", {"joya", "buenísimo", "de", "nos", "de una"}},
        {"fijate", {"si", "que", "en"}},
        {"voy", {"a", "para", "por"}},
        {"vamos", {"a", "para", "por"}},
        {"estoy", {"en", "con", "aquí", "muy", "acá"}},
        {"tengo", {"que", "un", "una", "hambre", "sueño"}},
        {"quiero", {"ir", "ver", "saber", "un"}},
        {"fin", {"de", "semana", "del"}},
        {"un", {"poco", "rato", "abrazo", "toque"}}
    };

    int vocabSize = model.getVocabSize();
    std::vector<int32_t> allCandIds(vocabSize);
    for (int i = 0; i < vocabSize; ++i) allCandIds[i] = i;

    std::vector<float> logits(vocabSize);
    int32_t tokenBuf[32];
    std::vector<float> hidden(model.getModelDim());

    int correctCount = 0;

    std::cout << "=================================================================" << std::endl;
    std::cout << "📊 EVALUACIÓN EN VIVO ('es' - C++ MicroTransformerModelCpp):" << std::endl;
    std::cout << "=================================================================" << std::endl;

    for (const auto& item : items) {
        int numTokens = model.tokenize(item.input.c_str(), item.input.length(), tokenBuf, 32);
        model.forward(tokenBuf, numTokens, hidden.data());
        model.scoreCandidates(hidden.data(), allCandIds.data(), vocabSize, logits.data());

        // Top 3
        std::vector<std::pair<float, int32_t>> scored;
        scored.reserve(vocabSize);
        for (int i = 0; i < vocabSize; ++i) {
            if (logits[i] > -10000.0f) {
                scored.push_back({logits[i], i});
            }
        }
        std::partial_sort(scored.begin(), scored.begin() + std::min((size_t)3, scored.size()), scored.end(),
                          [](const auto& a, const auto& b) { return a.first > b.first; });

        std::vector<std::string> preds;
        for (size_t i = 0; i < std::min((size_t)3, scored.size()); ++i) {
            std::string s = model.getTokenText(scored[i].second);
            // Trim leading space
            while (!s.empty() && (s.front() == ' ' || s.front() == '\t')) {
                s.erase(s.begin());
            }
            if (!s.empty()) preds.push_back(s);
        }

        bool match = false;
        for (const auto& p : preds) {
            for (const auto& exp : item.expected) {
                if (p == exp) {
                    match = true;
                    break;
                }
            }
            if (match) break;
        }

        if (match) correctCount++;
        std::cout << (match ? "✅" : "❌") << " '" << item.input << "' -> Predicciones: [";
        for (size_t i = 0; i < preds.size(); ++i) {
            if (i > 0) std::cout << ", ";
            std::cout << "'" << preds[i] << "'";
        }
        std::cout << "] | Esperadas: [";
        for (size_t i = 0; i < item.expected.size(); ++i) {
            if (i > 0) std::cout << ", ";
            std::cout << "'" << item.expected[i] << "'";
        }
        std::cout << "]" << std::endl;
    }

    double acc = (100.0 * correctCount) / items.size();
    std::cout << "🎯 Precisión Contextual (es): " << std::fixed << std::setprecision(1) << acc
              << "% (" << correctCount << "/" << items.size() << ")" << std::endl;
    std::cout << "=================================================================" << std::endl;

    // --- BENCHMARK ---
    const auto& wordStarts = model.getWordStartTokenIds();
    const int iterations = 10000;

    // Warmup
    for (int i = 0; i < 500; ++i) {
        int numTokens = model.tokenize("buenos", 6, tokenBuf, 32);
        model.forward(tokenBuf, numTokens, hidden.data());
        model.scoreCandidates(hidden.data(), wordStarts.data(), wordStarts.size(), logits.data());
    }

    // 1. Full Pipeline Benchmark (Tokenize + Forward + ScoreCandidates)
    auto benchStart = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < iterations; ++i) {
        int numTokens = model.tokenize("buenos", 6, tokenBuf, 32);
        model.forward(tokenBuf, numTokens, hidden.data());
        model.scoreCandidates(hidden.data(), wordStarts.data(), wordStarts.size(), logits.data());
    }
    auto benchEnd = std::chrono::high_resolution_clock::now();

    double totalMs = std::chrono::duration<double, std::milli>(benchEnd - benchStart).count();
    double usPerPass = (totalMs * 1000.0) / iterations;
    double passesPerSec = iterations / (totalMs / 1000.0);

    std::cout << "⚡ C++ Benchmark (" << iterations << " iters with " << wordStarts.size() << " word-start candidates):" << std::endl;
    std::cout << "   - Tiempo total: " << std::fixed << std::setprecision(2) << totalMs << " ms" << std::endl;
    std::cout << "   - Latencia media: " << std::fixed << std::setprecision(2) << usPerPass << " µs / inferencia" << std::endl;
    std::cout << "   - Throughput: " << std::fixed << std::setprecision(0) << passesPerSec << " inferencias / seg" << std::endl;

    // 2. Forward Pass Only Benchmark
    auto fwdStart = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < iterations * 5; ++i) {
        tokenBuf[0] = 10;
        model.forward(tokenBuf, 1, hidden.data());
    }
    auto fwdEnd = std::chrono::high_resolution_clock::now();
    double fwdUs = (std::chrono::duration<double, std::milli>(fwdEnd - fwdStart).count() * 1000.0) / (iterations * 5);
    std::cout << "   - Forward pass aislado: " << std::fixed << std::setprecision(2) << fwdUs << " µs / pass" << std::endl;

    // 3. Fast Top-K Scoring Benchmark
    int32_t topTokens[3];
    float topScores[3];
    auto topkStart = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < iterations; ++i) {
        model.scoreTopK(hidden.data(), wordStarts.data(), wordStarts.size(), 3, topTokens, topScores);
    }
    auto topkEnd = std::chrono::high_resolution_clock::now();
    double topkUs = (std::chrono::duration<double, std::milli>(topkEnd - topkStart).count() * 1000.0) / iterations;
    std::cout << "   - Fast scoreTopK (Heap K=3): " << std::fixed << std::setprecision(2) << topkUs << " µs / query" << std::endl;

    return 0;
}
