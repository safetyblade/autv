from refresh_pluto_sessions import get_session

if __name__ == "__main__":
    try:
        get_session()
        print("Pluto boot request accepted; playback not yet verified")
    except Exception as exc:
        print(f"::warning::Pluto boot failure: {type(exc).__name__}: {str(exc)[:120]}")
        raise SystemExit(1)
