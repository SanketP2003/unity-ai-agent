using UnityEngine;

public class FinishTrigger : MonoBehaviour
{
    void OnTriggerEnter(Collider other)
    {
        if (other.CompareTag("Player"))
        {
            GameObject gm = GameObject.FindObjectOfType<GameManager>();
            if (gm != null)
            {
                gm.GetComponent<GameManager>().ReachFinish();
            }
        }
    }
}
