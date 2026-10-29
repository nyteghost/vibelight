#pragma once

// Nonblocking SDL confirmation overlay. The streaming loop remains live so a
// missing response can time out; an SDL_ShowMessageBox would block forever.
#include "SDL_compat.h"
#include <SDL_ttf.h>
#include "path.h"

#include <QByteArray>
#include <cstdio>
#include <string>

class IdlePresencePrompt
{
public:
    enum class Answer { None, Continue, Disconnect };
    ~IdlePresencePrompt() { close(); }

    bool visible() const { return m_Window != nullptr; }
    Uint32 windowId() const { return m_Window ? SDL_GetWindowID(m_Window) : 0; }

    bool show(SDL_Window* parent)
    {
        if (!parent || m_Window) return false;
        m_FontData = Path::readDataFile("ModeSeven.ttf");
        if (m_FontData.isEmpty() || TTF_Init() != 0) return false;
        m_TtfInitialized = true;

        m_Font = TTF_OpenFontRW(
            SDL_RWFromConstMem(m_FontData.constData(), m_FontData.size()), 1, 23);
        m_SmallFont = TTF_OpenFontRW(
            SDL_RWFromConstMem(m_FontData.constData(), m_FontData.size()), 1, 15);
        if (!m_Font || !m_SmallFont) {
            close();
            return false;
        }

        m_Parent = parent;
        const char* hint = SDL_GetHint(SDL_HINT_VIDEO_MINIMIZE_ON_FOCUS_LOSS);
        m_HadMinimizeHint = hint != nullptr;
        m_PreviousMinimizeHint = hint ? hint : "";
        SDL_SetHint(SDL_HINT_VIDEO_MINIMIZE_ON_FOCUS_LOSS, "0");

        int px = 0, py = 0, width = 0, height = 0;
        SDL_GetWindowPosition(parent, &px, &py);
        SDL_GetWindowSize(parent, &width, &height);
        Uint32 flags = SDL_WINDOW_HIDDEN | SDL_WINDOW_BORDERLESS;
#if SDL_VERSION_ATLEAST(2, 0, 5)
        flags |= SDL_WINDOW_ALWAYS_ON_TOP | SDL_WINDOW_SKIP_TASKBAR;
#endif
        m_Window = SDL_CreateWindow(
            "Vibelight - Is anyone there?",
            px + (width - kWidth) / 2, py + (height - kHeight) / 2,
            kWidth, kHeight, flags);
        if (!m_Window) {
            close();
            return false;
        }
        m_LastCountdown = ~0u;
        SDL_ShowWindow(m_Window);
        SDL_RaiseWindow(m_Window);
#if SDL_VERSION_ATLEAST(2, 0, 5)
        SDL_SetWindowInputFocus(m_Window);
#endif
        render(120);
        return true;
    }

    void update(unsigned int secondsLeft)
    {
        if (!m_Window || m_LastCountdown == secondsLeft) return;
        render(secondsLeft);
    }

    Answer handleEvent(const SDL_Event& event) const
    {
        if (!m_Window) return Answer::None;
        const Uint32 id = windowId();

        if (event.type == SDL_KEYDOWN && !event.key.repeat) {
            // Keyboard confirmation works even if focus stayed on the stream.
            if (event.key.keysym.sym == SDLK_y ||
                event.key.keysym.sym == SDLK_RETURN ||
                event.key.keysym.sym == SDLK_KP_ENTER)
                return Answer::Continue;
            if (event.key.keysym.sym == SDLK_n ||
                event.key.keysym.sym == SDLK_ESCAPE)
                return Answer::Disconnect;
        }
        if (event.type == SDL_CONTROLLERBUTTONDOWN) {
            if (event.cbutton.button == SDL_CONTROLLER_BUTTON_A) return Answer::Continue;
            if (event.cbutton.button == SDL_CONTROLLER_BUTTON_B) return Answer::Disconnect;
        }
        if (event.type == SDL_MOUSEBUTTONUP && event.button.windowID == id &&
            event.button.button == SDL_BUTTON_LEFT) {
            const int x = event.button.x, y = event.button.y;
            if (x >= 32 && x < 300 && y >= 156 && y < 208) return Answer::Continue;
            if (x >= 316 && x < 528 && y >= 156 && y < 208) return Answer::Disconnect;
        }
        return Answer::None;
    }

    void close()
    {
        if (m_Window) {
            SDL_DestroyWindow(m_Window);
            m_Window = nullptr;
        }
        if (m_Font) {
            TTF_CloseFont(m_Font);
            m_Font = nullptr;
        }
        if (m_SmallFont) {
            TTF_CloseFont(m_SmallFont);
            m_SmallFont = nullptr;
        }
        if (m_TtfInitialized) {
            TTF_Quit();
            m_TtfInitialized = false;
        }
        if (m_Parent) {
            if (m_HadMinimizeHint)
                SDL_SetHint(SDL_HINT_VIDEO_MINIMIZE_ON_FOCUS_LOSS, m_PreviousMinimizeHint.c_str());
            else
                SDL_ResetHint(SDL_HINT_VIDEO_MINIMIZE_ON_FOCUS_LOSS);
            m_Parent = nullptr;
        }
        m_FontData.clear();
    }

private:
    static constexpr int kWidth = 560;
    static constexpr int kHeight = 238;

    void print(SDL_Surface* dst, TTF_Font* font, const std::string& text,
               int centerX, int top, SDL_Color color) const
    {
        if (!font || !dst) return;
        SDL_Surface* label = TTF_RenderUTF8_Blended(font, text.c_str(), color);
        if (!label) return;
        SDL_Rect area = { centerX - label->w / 2, top, label->w, label->h };
        SDL_BlitSurface(label, nullptr, dst, &area);
        SDL_FreeSurface(label);
    }

    void render(unsigned int seconds)
    {
        if (!m_Window) return;
        SDL_Surface* dst = SDL_GetWindowSurface(m_Window);
        if (!dst) return;
        m_LastCountdown = seconds;

        const Uint32 dark = SDL_MapRGB(dst->format, 23, 18, 28);
        const Uint32 outline = SDL_MapRGB(dst->format, 229, 122, 88);
        const Uint32 yes = SDL_MapRGB(dst->format, 50, 120, 86);
        const Uint32 no = SDL_MapRGB(dst->format, 115, 49, 59);
        SDL_FillRect(dst, nullptr, outline);
        SDL_Rect inner = { 3, 3, kWidth - 6, kHeight - 6 };
        SDL_FillRect(dst, &inner, dark);
        SDL_Rect yesRect = { 32, 156, 268, 52 };
        SDL_Rect noRect = { 316, 156, 212, 52 };
        SDL_FillRect(dst, &yesRect, yes);
        SDL_FillRect(dst, &noRect, no);

        const SDL_Color title = { 255, 238, 224, 255 };
        const SDL_Color hint = { 218, 205, 203, 255 };
        print(dst, m_Font, "Is anyone there?", kWidth / 2, 22, title);
        print(dst, m_SmallFont, "No input for 5 hours. Are you still using this stream?",
              kWidth / 2, 69, hint);
        char countdown[112] = {};
        std::snprintf(countdown, sizeof(countdown),
                      "Disconnecting in %u:%02u if there's no answer",
                      seconds / 60u, seconds % 60u);
        print(dst, m_SmallFont, countdown, kWidth / 2, 105, hint);
        print(dst, m_SmallFont, "Yes - keep streaming (Y / Enter)", 166, 173, title);
        print(dst, m_SmallFont, "No - disconnect (N)", 422, 173, title);
        SDL_UpdateWindowSurface(m_Window);
    }

    SDL_Window* m_Parent = nullptr;
    SDL_Window* m_Window = nullptr;
    TTF_Font* m_Font = nullptr;
    TTF_Font* m_SmallFont = nullptr;
    QByteArray m_FontData;
    bool m_TtfInitialized = false;
    bool m_HadMinimizeHint = false;
    std::string m_PreviousMinimizeHint;
    unsigned int m_LastCountdown = ~0u;
};
