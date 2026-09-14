using UnityEngine;
using UnityEngine.UI;

public class GameManager : MonoBehaviour
{
    public static GameManager Instance { get; private set; }

    [Header("UI References")]
    public Text coinText;
    public Text winText;

    private int coinCount = 0;
    private const int coinsNeeded = 5;
    private bool levelCompleted = false;

    void Awake()
    {
        if (Instance != null && Instance != this)
        {
            Destroy(gameObject);
            return;
        }
        Instance = this;
        DontDestroyOnLoad(gameObject);
        UpdateCoinUI();
        if (winText != null)
            winText.gameObject.SetActive(false);
    }

    public void AddCoin()
    {
        if (levelCompleted) return;
        coinCount++;
        UpdateCoinUI();
        if (coinCount >= coinsNeeded)
        {
            // All coins collected, wait for finish trigger
        }
    }

    private void UpdateCoinUI()
    {
        if (coinText != null)
            coinText.text = $"Coins: {coinCount}/{coinsNeeded}";
    }

    public void CompleteLevel()
    {
        if (levelCompleted) return;
        levelCompleted = true;
        if (winText != null)
        {
            winText.gameObject.SetActive(true);
            winText.text = "You Win!";
        }
    }
}
