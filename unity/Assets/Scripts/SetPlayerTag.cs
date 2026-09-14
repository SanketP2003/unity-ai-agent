using UnityEngine;

public class SetPlayerTag : MonoBehaviour
{
    void Awake()
    {
        gameObject.tag = "Player";
    }
}
