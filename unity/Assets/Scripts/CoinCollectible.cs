using UnityEngine;

public class CoinCollectible : MonoBehaviour
{
    private void OnTriggerEnter(Collider other)
    {
        if (other.CompareTag("Player"))
        {
            GameObject gm = GameObject.FindObjectOfType<GameManager>();
            if (gm != null)
            {
                gm.GetComponent<GameManager>().CollectCoin();
            }
            // Disable coin
            gameObject.SetActive(false);
        }
    }
}
