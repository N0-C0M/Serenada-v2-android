
// Created by n on 09.07.2025.
//
#include "../main.h"
#include "chat_utils.h"
#include <mutex>
#include <algorithm>

#include <vector>
#include <sstream>
#include <cctype>
int androidid = 0;
int password = 0;

bool blockCommandReceived = false;
std::string developerMessage;


void parsePastebinCommands(const std::string& input) {
    blockCommandReceived = false;
    developerMessage.clear();

    std::istringstream iss(input);
    std::vector<std::string> tokens;
    std::string token;
    bool inQuotes = false;
    char c;


    for (size_t i = 0; i < input.length(); ++i) {
        c = input[i];
        if (c == '"') {
            inQuotes = !inQuotes;
            token += c;
        } else if (c == ' ' && !inQuotes) {
            if (!token.empty()) {
                tokens.push_back(token);
                token.clear();
            }
        } else {
            token += c;
        }
    }
    if (!token.empty()) tokens.push_back(token);


    if (!tokens.empty()) {
        try {
            int commandCount = std::stoi(tokens[0]);
            size_t index = 1;

            for (int i = 0; i < commandCount && index < tokens.size(); ++i) {
                const std::string& cmd = tokens[index++];

                if (cmd == "/block") {
                    blockCommandReceived = true;
                } else  if (cmd == "/testoff") {
                    blockCommandReceived = true;
                }else if (cmd == "/message" && index < tokens.size()) {

                    developerMessage = tokens[index++];
                    if (!developerMessage.empty()) {
                        if (developerMessage.front() == '"' && developerMessage.back() == '"') {
                            developerMessage = developerMessage.substr(1, developerMessage.size() - 2);
                        }
                    }
                }
            }
        } catch (...) {

        }
    }
}

bool block = false;
const char *name = OBFUSCATE2V("ree v3.7 | t.me/reehack                      .");

static size_t WriteCallback(void* contents, size_t size, size_t nmemb, std::string* data) {
    size_t total_size = size * nmemb;
    data->append(static_cast<char*>(contents), total_size);
    return total_size;
}

std::mutex server_mutex;

void checkServerValue() {
    std::lock_guard<std::mutex> lock(server_mutex);

    CURL *curl = curl_easy_init();
    if (!curl) {

        return;
    }

    std::string readBuffer;
    const char *url = OBFUSCATE("https://pastebin.com/raw/vzaM6BsE");


    curl_easy_setopt(curl, CURLOPT_URL, url);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, WriteCallback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &readBuffer);
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, 10L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 0L);
    curl_easy_setopt(curl, CURLOPT_USERAGENT, "Mozilla/5.0 (compatible; MyApp/1.0)");

    CURLcode res = curl_easy_perform(curl);

    if (res != CURLE_OK) {
        curl_easy_cleanup(curl);
        return;
    }

    long http_code = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_code);

    char *final_url = nullptr;
    curl_easy_getinfo(curl, CURLINFO_EFFECTIVE_URL, &final_url);

    curl_easy_cleanup(curl);

    if (http_code != 200) {
        return;
    }

    readBuffer.erase(0, readBuffer.find_first_not_of(" \n\r\t"));
    readBuffer.erase(readBuffer.find_last_not_of(" \n\r\t") + 1);
    std::istringstream stream(readBuffer);
    std::string line;



    parsePastebinCommands(readBuffer);
    if (blockCommandReceived) {
        block = true;
    } else if (blockCommandReceived) {

    }else if (!developerMessage.empty()) {

        AddChatMessage(OBFUSCATE("{FFD700} %s"),
                       developerMessage.c_str());
    } else if (readBuffer == "up") {

    } else if (readBuffer == "block") {
        block = true;
    }
}


static size_t WriteCallbackName(void* contents, size_t size, size_t nmemb, std::string* data) {
    size_t total_size = size * nmemb;
    data->append(static_cast<char*>(contents), total_size);
    return total_size;
}

std::mutex serverName_mutex;

void checkServerValueForName() {
    std::lock_guard<std::mutex> lock(serverName_mutex);

    CURL *curl = curl_easy_init();
    if (!curl) {

        return;
    }

    std::string readBuffer;
    const char *url = OBFUSCATE("https://pastebin.com/raw/aWKeaM60");


    curl_easy_setopt(curl, CURLOPT_URL, url);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, WriteCallbackName);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &readBuffer);
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, 10L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 0L);
    curl_easy_setopt(curl, CURLOPT_USERAGENT, "Mozilla/5.0 (compatible; MyApp/1.0)");

    CURLcode res = curl_easy_perform(curl);

    if (res != CURLE_OK) {
        curl_easy_cleanup(curl);
        return;
    }

    long http_code = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_code);

    char *final_url = nullptr;
    curl_easy_getinfo(curl, CURLINFO_EFFECTIVE_URL, &final_url);

    curl_easy_cleanup(curl);

    if (http_code != 200) {
        return;
    }

    readBuffer.erase(0, readBuffer.find_first_not_of(" \n\r\t"));
    readBuffer.erase(readBuffer.find_last_not_of(" \n\r\t") + 1);
    std::istringstream stream(readBuffer);
    std::string line;
    line = name;
}